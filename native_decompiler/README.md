# LuaJIT Decompiler v2 - Android Native Port

Android Native (NDK) порт LuaJIT Decompiler v2 с интегрированной поддержкой unprot для снятия защиты с байткода.

## 📋 Описание

Этот проект представляет собой нативную библиотеку для Android, которая объединяет:
- **LuaJIT Decompiler v2** — современный декомпилятор байткода LuaJIT с поддержкой gotos и stripped bytecode
- **Unprot** — инструмент для снятия защиты с LuaJIT байткода (anti-decompiler unprotector)

Библиотека интегрируется в Android приложение через JNI и позволяет декомпилировать защищённые LuaJIT файлы прямо на устройстве.

## 🔨 Сборка

### Требования

- **Android NDK** (рекомендуется r21e или новее)
- **C++ компилятор** с поддержкой C++20
- **Android SDK**

### Инструкции по сборке

1. **Установите Android NDK**
   ```bash
   # Убедитесь, что NDK установлен и доступен в PATH
   export NDK_HOME=/path/to/android-ndk
   ```

2. **Перейдите в директорию проекта**
   ```bash
   cd native_decompiler/jni
   ```

3. **Настройте пути в Android.mk** (если необходимо)
   ```makefile
   # Убедитесь, что LOCAL_PATH указывает на правильную директорию
   ```

4. **Соберите библиотеку**
   ```bash
   # Для всех архитектур
   $NDK_HOME/ndk-build
   
   # Или для конкретной архитектуры
   $NDK_HOME/ndk-build APP_ABI=arm64-v8a
   $NDK_HOME/ndk-build APP_ABI=armeabi-v7a
   ```

5. **Результат**
   - Скомпилированные библиотеки будут в `../libs/<arch>/libluajit-decompiler.so` (отсюда можно взять уже готовые с репозитория)
   - Объектные файлы будут в `../obj/`


## 📦 Структура проекта

```
native_decompiler/
├── jni/
│   ├── Android.mk          # Makefile для сборки
│   ├── Application.mk      # Конфигурация приложения
│   ├── main.cpp            # Точка входа, JNI обёртки
│   ├── main.h              # Основные заголовки
│   ├── bytecode/           # Парсинг байткода LuaJIT
│   │   ├── bytecode.cpp
│   │   ├── bytecode.h
│   │   ├── prototype.cpp
│   │   ├── prototype.h
│   │   ├── unprot.cpp      # Unprot модуль
│   │   └── unprot.h
│   ├── ast/                # Построение AST
│   │   ├── ast.cpp
│   │   ├── ast.h
│   │   └── ...
│   └── lua/                # Генерация Lua кода
│       ├── lua.cpp
│       └── lua.h
├── libs/                   # Скомпилированные библиотеки
│   ├── arm64-v8a/
│   └── armeabi-v7a/
└── README.md               # Этот файл
```

## 🎯 Возможности

- ✅ Декомпиляция LuaJIT байткода версий 1 и 2
- ✅ Поддержка gotos
- ✅ Работа со stripped bytecode (без отладочной информации)
- ✅ Автоматическое снятие защиты unprot перед декомпиляцией
- ✅ Восстановление локальных переменных и upvalues
- ✅ Поддержка всех типов констант (строки, числа, таблицы, прототипы)
- ✅ JNI интерфейс для интеграции в Android приложения

## 📝 Использование

Библиотека предоставляет JNI функции для Java/Kotlin кода (перед этим нужно правильно настроить Java классы у JNIEXPORT функций в main.cpp, текущий класс - Java_com_arzmod_radare_LuaJITDecompiler):

```java
// Проверка, является ли файл LuaJIT байткодом
boolean isValid = isFileHaveBC(filePath);

// Декомпиляция файлов
int result = decompileFiles(
    inputPath, outputPath,
    forceOverwrite,
    ignoreDebugInfo,
    minimizeDiffs,
    unrestrictedAscii,
    silentAssertions,
    extensionFilter
);
```

## 👥 Авторы 

### Оригинальный декомпилятор
**LuaJIT Decompiler v2**  

Автор: [marsinator358](https://github.com/marsinator358)  
Репозиторий: [luajit-decompiler-v2](https://github.com/marsinator358/luajit-decompiler-v2)

### Unprot модуль
**Lua anti-decompiler unprotector**  (moved from python script to c++)

Автор: sᴀxᴏɴ  
Оригинальная тема: [BLASTHACK Forum](https://www.blast.hk/threads/185217/)  

### Порт на Android
Адаптация для Android NDK и интеграция unprot модуля выполнена автором проекта **arzmod-patcher** - [idmkdev](https://github.com/idmkdev)  .

## 📄 Лицензия

Смотрите файл [LICENSE](jni/LICENSE) в директории `jni/`.

## 🔗 Полезные ссылки

- [Оригинальный LuaJIT Decompiler v2](https://github.com/marsinator358/luajit-decompiler-v2)
- [Unprot - Lua anti-decompiler unprotector](https://www.blast.hk/threads/185217/)

---

**Примечание**: Этот проект является портом оригинальных инструментов для платформы Android. Все права на оригинальный код принадлежат их авторам.








