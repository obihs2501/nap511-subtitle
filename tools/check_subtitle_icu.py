"""Validate subtitle regexes with Windows' native ICU, without an emulator/build.

Run from anywhere: python tools/check_subtitle_icu.py [--sample path/to/file.ass]
This checks the ICU syntax that differs from the desktop JVM. It does not replace
an Android UI/playback test. Sample contents are never printed or uploaded.
"""
import argparse
import ctypes
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sample", type=Path)
    args = parser.parse_args()
    icu = ctypes.CDLL("icu.dll")
    icu.uregex_open.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.c_uint32,
                              ctypes.c_void_p, ctypes.POINTER(ctypes.c_int32)]
    icu.uregex_open.restype = ctypes.c_void_p
    icu.uregex_close.argtypes = [ctypes.c_void_p]
    icu.u_errorName.argtypes = [ctypes.c_int32]
    icu.u_errorName.restype = ctypes.c_char_p
    icu.uregex_setText.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int32,
                                 ctypes.POINTER(ctypes.c_int32)]
    icu.uregex_findNext.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_int32)]
    icu.uregex_findNext.restype = ctypes.c_int8
    source = (Path(__file__).resolve().parents[1] / "app/src/main/java/github/zerorooot/nap511/util/SubtitleConvertUtil.kt").read_text(encoding="utf-8")
    patterns = re.findall(r'private val (\w+) = Regex\("""(.*?)"""\)', source)
    assert patterns, "No raw-string subtitle regexes found"
    for name, pattern in patterns:
        encoded = pattern.encode("utf-16-le")
        buffer = ctypes.create_string_buffer(encoded)
        error = ctypes.c_int32(0)
        compiled = icu.uregex_open(buffer, len(encoded) // 2, 0, None, ctypes.byref(error))
        assert error.value <= 0, (name, icu.u_errorName(error.value).decode())
        try:
            print(f"PASS ICU: {name}")
            if args.sample and name == "assOverride":
                text = args.sample.read_text(encoding="utf-8-sig")
                data = text.encode("utf-16-le")
                text_buffer = ctypes.create_string_buffer(data)
                icu.uregex_setText(compiled, text_buffer, len(data) // 2, ctypes.byref(error))
                count = 0
                while icu.uregex_findNext(compiled, ctypes.byref(error)):
                    count += 1
                assert error.value <= 0, icu.u_errorName(error.value).decode()
                dialogues = sum(line.lower().startswith("dialogue:") for line in text.splitlines())
                print(f"Sample checked locally: {dialogues} dialogue records, {count} override blocks")
        finally:
            icu.uregex_close(compiled)
    print("All subtitle regexes accepted by native ICU.")


if __name__ == "__main__":
    main()
