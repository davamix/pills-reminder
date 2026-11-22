"""Reads a bugs file and applies one bug. Used by run.sh; see SKILL.md for the format.

    python -I apply_bug.py list <bugs file>          one line per bug: its ID, a tab, its file
    python -I apply_bug.py apply <bugs file> <ID>    applies that bug to its file, or exits 1 saying why
"""
import pathlib
import sys


def parse(path):
    """The bugs, in order: (id, file, [(old, new), ...])."""
    bugs = []
    bug = None
    block = None  # "old" or "new", while reading one
    lines = []

    def close_block():
        nonlocal block, lines
        if block is None:
            return
        text = "\n".join(lines)
        if block == "old":
            bug["pairs"].append([text, None])
        else:
            if not bug["pairs"] or bug["pairs"][-1][1] is not None:
                sys.exit(f"{bug['id']}: a '--- new' block without its '--- old' block")
            bug["pairs"][-1][1] = text
        block, lines = None, []

    for raw in pathlib.Path(path).read_text(encoding="utf-8").split("\n"):
        line = raw.rstrip("\r")
        if line.startswith("=== "):
            close_block()
            bug = {"id": line[4:].strip(), "file": None, "pairs": []}
            bugs.append(bug)
        elif bug is None or (block is None and (line.startswith("#") or not line.strip())):
            continue
        elif block is None and line.startswith("file:"):
            bug["file"] = line[5:].strip()
        elif line in ("--- old", "--- new"):
            close_block()
            block = line[4:]
        elif block is not None:
            lines.append(line)
        else:
            sys.exit(f"{bug['id']}: unexpected line outside a block: {line}")
    close_block()

    seen = set()
    for b in bugs:
        if not b["id"] or b["id"] in seen:
            sys.exit(f"a bug ID is empty or repeated: '{b['id']}'")
        seen.add(b["id"])
        if not b["file"]:
            sys.exit(f"{b['id']}: no 'file:' line")
        if not b["pairs"] or any(new is None for _, new in b["pairs"]):
            sys.exit(f"{b['id']}: needs '--- old' and '--- new' blocks, in pairs")
    return bugs


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("list", "apply"):
        sys.exit(__doc__)
    bugs = parse(sys.argv[2])
    if sys.argv[1] == "list":
        for b in bugs:
            print(f"{b['id']}\t{b['file']}")
        return
    wanted = sys.argv[3] if len(sys.argv) > 3 else ""
    bug = next((b for b in bugs if b["id"] == wanted), None)
    if bug is None:
        sys.exit(f"no bug with the ID '{wanted}'")
    target = pathlib.Path(bug["file"])
    text = target.read_text(encoding="utf-8")
    for old, new in bug["pairs"]:
        count = text.count(old)
        if count != 1:
            sys.exit(f"{bug['id']}: its old text is in {target} {count} times, not once: {old[:80]!r}")
        text = text.replace(old, new, 1)
    target.write_text(text, encoding="utf-8", newline="")


if __name__ == "__main__":
    main()
