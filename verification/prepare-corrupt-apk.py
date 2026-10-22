"""Create a tampered signed TEST APK; never reads or installs a released app."""
import pathlib
import struct
import sys
import zipfile

source = pathlib.Path(sys.argv[1])
target = pathlib.Path(sys.argv[2])
data = bytearray(source.read_bytes())
with zipfile.ZipFile(source) as archive:
    entry = archive.getinfo('classes.dex')
name_length, extra_length = struct.unpack_from('<HH', data, entry.header_offset + 26)
data[entry.header_offset + 30 + name_length + extra_length + 10] ^= 1
target.write_bytes(data)
