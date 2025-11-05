#include "../main.h"
#include "unprot.h"

namespace {

struct ProtoInfo {
	uint32_t pos = 0;              // position of size ULEB128 (start of block)
	uint32_t size = 0;             // payload size (without size field itself)
	uint32_t sizeLength = 0;       // byte length of size ULEB128
	uint32_t fullSize = 0;         // total bytes of the block: sizeLength + size
	uint32_t flags = 0;
	uint32_t params = 0;
	uint32_t framesize = 0;
	uint32_t numuv = 0;
	uint32_t numkgc = 0;           // value (unused but kept for parity)
	uint32_t numkgcLength = 0;
	uint32_t numkn = 0;
	uint32_t numknLength = 0;
	uint32_t numbc = 0;
	uint32_t numbcPos = 0;         // position of numbc ULEB128 (inside block buffer)
	uint32_t numbcLength = 0;      // length in bytes of numbc ULEB128
	uint32_t ins = 0;              // absolute position of instruction stream (within whole file data)
};

static inline bool is_luajit_header(const std::vector<uint8_t>& data) {
	return data.size() > 5 && data[0] == 0x1B && data[1] == 0x4C && data[2] == 0x4A;
}

static inline uint32_t read_uleb128(const std::vector<uint8_t>& data, uint32_t offset, uint32_t& outLen) {
	uint64_t value = 0;
	uint32_t i = 0;
	for (;; i++) {
		uint8_t byte = data[offset + i];
		value |= (uint64_t)(byte & 0x7F) << (i * 7);
		if ((byte & 0x80) == 0) break;
	}
	outLen = i + 1;
	return (uint32_t)value;
}

static inline std::vector<uint8_t> write_uleb128(uint32_t value) {
	std::vector<uint8_t> out;
	do {
		uint8_t byte = value & 0x7F;
		value >>= 7;
		if (value != 0) byte |= 0x80;
		out.push_back(byte);
	} while (value != 0);
	return out;
}

// Python read_uint16 equivalent
// If hi >= 0x81 -> ((hi - 0x80) << 8) | lo ; else -> lo
static inline uint16_t read_uint16_special(uint8_t lo, uint8_t hi) {
	int16_t b = (int16_t)hi - 0x80;
	if (b > 0) return (uint16_t)(((uint16_t)b << 8) | (uint16_t)lo);
	return (uint16_t)lo;
}

static inline uint32_t new_dist(uint32_t offsetBytes, uint32_t dist) {
	uint32_t opcodePos = offsetBytes / 4;
	return (opcodePos + dist + 1) * 4;
}

static std::vector<ProtoInfo> collect_protos(const std::vector<uint8_t>& data) {
	std::vector<ProtoInfo> protos;
	if (!is_luajit_header(data)) return protos;
	uint32_t i = 5; // after header (3 magic + version + flags)
	while (i < data.size()) {
		if (data[i] == 0) break; // terminator
		ProtoInfo pi;
		pi.pos = i;
		uint32_t lenLen = 0;
		pi.size = read_uleb128(data, i, lenLen);
		pi.sizeLength = lenLen;
		pi.fullSize = pi.size + pi.sizeLength;
		uint32_t p = i + pi.sizeLength;
		// header fields
		pi.flags = data[p + 0];
		pi.params = data[p + 1];
		pi.framesize = data[p + 2];
		pi.numuv = data[p + 3];
		p += 4;
		// numkgc
		pi.numkgc = read_uleb128(data, p, pi.numkgcLength);
		p += pi.numkgcLength;
		// numkn
		pi.numkn = read_uleb128(data, p, pi.numknLength);
		p += pi.numknLength;
		// numbc
		pi.numbcPos = p;
		pi.numbc = read_uleb128(data, p, pi.numbcLength);
		p += pi.numbcLength;
		// instruction stream absolute start
		pi.ins = p;
		protos.push_back(pi);
		i += pi.fullSize;
	}
	return protos;
}

static bool unprot_proto(std::vector<uint8_t>& data, const ProtoInfo& pi) {
	if (!pi.numbc) return false;
	const uint32_t protoStart = pi.ins;
	const uint32_t protoEnd = pi.ins + pi.numbc * 4;
	if (protoEnd > data.size()) return false;

	uint32_t jumpOff = 0;
	uint32_t trashOpcodes = 0;
	uint32_t jumpMax = 0;

	// skip trash opcodes at the beginning
	for (;;) {
		if (protoStart + jumpOff + 5 > data.size()) break;
		uint8_t opcode = data[protoStart + jumpOff];
		uint8_t nextOpcode = (protoStart + jumpOff + 4 < data.size()) ? data[protoStart + jumpOff + 4] : 0;
		if (opcode == 0x12 && (nextOpcode == 0x58 || nextOpcode == 0x32)) {
			jumpOff += 4;
			trashOpcodes += 1;
			continue;
		}
		if (opcode == 0x58 || opcode == 0x32) {
			if (protoStart + jumpOff + 4 > data.size()) break;
			uint16_t jmpDist = (uint16_t)read_uint16_special(data[protoStart + jumpOff + 2], data[protoStart + jumpOff + 3]);
			jmpDist += 1;
			trashOpcodes += jmpDist;
			jumpOff += 4 * (uint32_t)jmpDist;
			continue;
		}
		break;
	}

	// ind maximal down-jump destination and first return past it
	static const uint8_t retOpcodes[] = { 0x49, 0x4A, 0x4B, 0x4C, 0x43, 0x44 };

	for (;;) {
		if (protoStart + jumpOff + 4 > protoEnd) return false; // no return found
		uint8_t opcode = data[protoStart + jumpOff];
		bool jmpDown = (data[protoStart + jumpOff + 3] >= 128);
		uint16_t uintv = (uint16_t)read_uint16_special(data[protoStart + jumpOff + 2], data[protoStart + jumpOff + 3]);

		if ((opcode == 0x32 || opcode == 0x58) && jmpDown) {
			uint32_t tmp = new_dist(jumpOff, uintv);
			if (tmp > jumpMax) jumpMax = tmp;
			jumpOff += 4;
			continue;
		}

		bool isRet = false;
		for (uint8_t ro : retOpcodes) { if (opcode == ro) { isRet = true; break; } }
		if (isRet) {
			if (jumpOff < jumpMax) { jumpOff += 4; continue; }
			// Apply edits:
			// 1) erase bytes after return
			uint32_t retNextAbs = protoStart + jumpOff + 4;
			if (retNextAbs < protoEnd) {
				data.erase(data.begin() + retNextAbs, data.begin() + protoEnd);
			}
			// 2) erase trash opcodes at the beginning
			if (trashOpcodes) {
				uint32_t trashBytes = trashOpcodes * 4;
				data.erase(data.begin() + protoStart, data.begin() + protoStart + trashBytes);
			}
			// Compute how many opcodes were removed in total
			uint32_t keptCount = (jumpOff / 4) - trashOpcodes + 1; // include return
			uint32_t totalOpcodesRemoved = pi.numbc > keptCount ? (pi.numbc - keptCount) : 0;
			// Update numbc ULEB128
			uint32_t newNumbc = pi.numbc - totalOpcodesRemoved;
			std::vector<uint8_t> newNumbcLeb = write_uleb128(newNumbc);
			// Note: positions shifted by earlier erases; recompute current numbc pos
			// We know: initial edits removed (protoEnd - retNextAbs) bytes after ret and (trashOpcodes*4) at start.
			// The numbc field is before protoStart; only the second erase affects absolute positions before protoStart? No.
			// Erasing at [protoStart, ...] shifts positions after it only; numbcPos remains the same.
			uint32_t curNumbcPos = pi.numbcPos;
			data.erase(data.begin() + curNumbcPos, data.begin() + curNumbcPos + pi.numbcLength);
			data.insert(data.begin() + curNumbcPos, newNumbcLeb.begin(), newNumbcLeb.end());
			int32_t extraLen = (int32_t)newNumbcLeb.size() - (int32_t)pi.numbcLength;
			// Update prototype block size ULEB128 at pi.pos
			uint32_t curProtoSize;
			uint32_t curSizeLen;
			curProtoSize = read_uleb128(data, pi.pos, curSizeLen);
			// proto payload size shrinks by removed bytes (totalOpcodesRemoved*4) and changes by extraLen in numbc field length
			int64_t newProtoSizeSigned = (int64_t)curProtoSize - (int64_t)totalOpcodesRemoved * 4 + (int64_t)extraLen;
			if (newProtoSizeSigned < 0) newProtoSizeSigned = 0;
			std::vector<uint8_t> newSizeLeb = write_uleb128((uint32_t)newProtoSizeSigned);
			data.erase(data.begin() + pi.pos, data.begin() + pi.pos + curSizeLen);
			data.insert(data.begin() + pi.pos, newSizeLeb.begin(), newSizeLeb.end());
			return true;
		}

		jumpOff += 4;
	}
	return false;
}

}

Unprot::Unprot(const std::string& filePath) : filePath(filePath) {}

void Unprot::operator()() {
	FILE* f = fopen(filePath.c_str(), "rb");
	if (!f) return;
	fseek(f, 0, SEEK_END);
	long sz = ftell(f);
	fseek(f, 0, SEEK_SET);
	if (sz <= 0) { fclose(f); return; }
	std::vector<uint8_t> data;
	data.resize((size_t)sz);
	if (fread(data.data(), 1, (size_t)sz, f) != (size_t)sz) { fclose(f); return; }
	fclose(f);

	if (!is_luajit_header(data)) return;

	auto protos = collect_protos(data);
	if (protos.empty()) return;
	bool changed = false;

	for (int i = (int)protos.size() - 1; i >= 0; --i) {
		if (unprot_proto(data, protos[(size_t)i])) changed = true;
	}

	if (!changed) return;

	FILE* wf = fopen(filePath.c_str(), "wb");
	if (!wf) return;
	if (fwrite(data.data(), 1, data.size(), wf) != data.size()) {
		fclose(wf);
		return;
	}
	fclose(wf);
}


