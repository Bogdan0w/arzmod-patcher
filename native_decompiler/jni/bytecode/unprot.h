#pragma once

#include <string>

class Unprot {
public:

	Unprot(const std::string& filePath);

	void operator()();

	const std::string filePath;
};


