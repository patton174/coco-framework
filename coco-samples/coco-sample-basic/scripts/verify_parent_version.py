from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[3]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def element_text(root: ET.Element, path: str) -> str:
    element = root.find(path, NAMESPACE)
    if element is None or element.text is None or not element.text.strip():
        raise SystemExit(f"Missing required POM element: {path}")
    return element.text.strip()


def main() -> int:
    root_pom = ET.parse(ROOT / "pom.xml").getroot()
    sample_pom = ET.parse(ROOT / "coco-samples" / "coco-sample-basic" / "pom.xml").getroot()
    root_revision = element_text(root_pom, "m:properties/m:revision")
    sample_parent_version = element_text(sample_pom, "m:parent/m:version")
    if sample_parent_version != root_revision:
        raise SystemExit(
            "coco-sample-basic parent version must match root revision: "
            f"expected {root_revision}, actual {sample_parent_version}"
        )
    print(f"sample parent version matches root revision: {root_revision}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
