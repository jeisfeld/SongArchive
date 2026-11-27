import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

from import_chants import read_document


IMPORTER = Path(__file__).with_name("import_chants.py")
DOCUMENT = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
  <w:p><w:pPr><w:pStyle w:val="Liedtitel"/></w:pPr>
    <w:r><w:t>X001</w:t><w:tab/><w:t>Morning</w:t></w:r>
    <w:r><w:t xml:space="preserve"> River </w:t></w:r>
    <w:r><w:rPr><w:rStyle w:val="Texterklrung"/></w:rPr><w:t>[A quiet song]</w:t></w:r>
    <w:r><w:tab/><w:t>(Ada Example)</w:t></w:r>
    <w:r><w:rPr><w:rStyle w:val="Texterklrung"/></w:rPr><w:t xml:space="preserve"> [Translation]</w:t></w:r>
    <w:r><w:tab/><w:t>Capo 2</w:t></w:r>
  </w:p>
  <w:p><w:pPr><w:pStyle w:val="Akkorde"/></w:pPr><w:r><w:t>Am</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="Additionaltext"/></w:pPr>
    <w:r><w:t>First line</w:t><w:br/><w:t>Second line</w:t><w:cr/><w:t>Third line</w:t></w:r>
  </w:p>
  <w:p><w:pPr><w:pStyle w:val="Additionaltext"/></w:pPr>
    <w:r><w:t>New paragraph</w:t></w:r>
  </w:p>
  <w:p><w:pPr><w:pStyle w:val="Liedtitel"/></w:pPr><w:r><w:t>X007</w:t><w:tab/><w:t>Another song</w:t></w:r></w:p>
  <w:p><w:pPr><w:pStyle w:val="Akkorde"/></w:pPr><w:r><w:t>D</w:t></w:r></w:p>
</w:body></w:document>"""


class ImportChantsTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        directory = Path(temporary.name)
        self.docx = directory / "Chants.docx"
        self.images = directory / "chants.zip"
        with ZipFile(self.docx, "w") as docx:
            docx.writestr("word/document.xml", DOCUMENT)
        with ZipFile(self.images, "w") as images:
            images.writestr("X001 Morning River.jpg", b"")
            images.writestr("X004 Image only.jpg", b"")

    def test_heading_preserves_comment_runs_and_plain_fields(self):
        sheet = read_document(self.docx)["X001"][0]

        self.assertEqual(sheet["title"], "Morning River [A quiet song]")
        self.assertEqual(sheet["details"], ["(Ada Example) [Translation]", "Capo 2"])
        self.assertEqual(sheet["headingRuns"], [
            [{"text": "Morning River"}, {"text": " [A quiet song]", "tone": "comment"}],
            [{"text": "(Ada Example)"}, {"text": " [Translation]", "tone": "comment"}],
            [{"text": "Capo 2"}],
        ])

    def test_additional_text_distinguishes_line_breaks_from_paragraphs(self):
        lines = read_document(self.docx)["X001"][0]["lines"]
        notes = [line for line in lines if line["kind"] == "note"]

        self.assertEqual([line["runs"][0]["text"] for line in notes], [
            "First line", "Second line", "Third line", "New paragraph",
        ])
        self.assertEqual([line["paragraphStart"] for line in notes], [True, False, False, True])

    def run_importer(self, *options):
        return subprocess.run(
            [sys.executable, str(IMPORTER), str(self.docx), "--images", str(self.images), "--image-only", *options],
            capture_output=True, text=True, check=False,
        )

    def test_image_only_song_is_reported_and_matches_still_export(self):
        result = self.run_importer("--format", "sql")

        self.assertEqual(result.returncode, 0)
        self.assertIn("X004", result.stderr)
        self.assertIn("WHERE id = 'X001';", result.stdout)
        self.assertNotIn("WHERE id = 'X004';", result.stdout)
        self.assertEqual(list(json.loads(self.run_importer().stdout)), ["X001"])

    def test_single_song_export_limits_sql_to_that_id(self):
        result = subprocess.run(
            [sys.executable, str(IMPORTER), str(self.docx), "--song", "x001", "--format", "sql"],
            capture_output=True, text=True, check=False,
        )

        self.assertEqual(result.returncode, 0)
        self.assertIn("WHERE id = 'X001';", result.stdout)
        self.assertNotIn("WHERE id = 'X007';", result.stdout)

    def test_strict_image_check_fails_for_missing_word_section(self):
        result = self.run_importer("--strict-images")

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn("X004", result.stderr)
