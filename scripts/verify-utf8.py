import subprocess, sys

def is_binary(data: bytes) -> bool:
    return b'\x00' in data

res = subprocess.run(["git", "ls-files", "-z"], capture_output=True, check=True)
paths = res.stdout.split(b"\x00")
bad = []

for p in paths:
    if not p:
        continue
    path = p.decode("utf-8", "surrogateescape")
    try:
        with open(path, "rb") as f:
            data = f.read()
        if is_binary(data):
            continue
        data.decode("utf-8")
    except UnicodeDecodeError:
        bad.append(path)

if bad:
    print("Non-UTF-8 files found:")
    for b in bad:
        print(f" - {b}")
    sys.exit(1)

print("All tracked text files are UTF-8.")
