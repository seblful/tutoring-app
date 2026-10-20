# /// script
# dependencies = ["fonttools>=4.63,<5", "skia-pathops>=0.9,<1"]
# ///
"""Embed a weight-matched BYN SVG in the fonts: uv run art/add_byn_glyph.py."""

from pathlib import Path
from xml.etree import ElementTree

import pathops
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from fontTools.ttLib import TTFont, newTable

GLYPH_NAME = "uni20C5"


def repair_vertical_metrics(font: TTFont) -> None:
    """The original generator omitted the appended glyph's vertical metric."""
    if "vmtx" not in font:
        return
    raw = font.getTableData("vmtx")
    count = font["maxp"].numGlyphs
    expected = 4 * font["vhea"].numberOfVMetrics + 2 * (
        count - font["vhea"].numberOfVMetrics
    )
    if len(raw) == expected - 2 and font.getGlyphOrder()[-1] == GLYPH_NAME:
        table = newTable("vmtx")
        table.decompile(raw + b"\0\0", font)
        font["vmtx"] = table
    # Load before extending the glyph order, including for unmodified source fonts.
    font["vmtx"].metrics[GLYPH_NAME] = font["vmtx"].metrics[font.getBestCmap()[0x0411]]


def hyphen_thickness(font: TTFont) -> int:
    glyph = font["glyf"][font.getBestCmap()[ord("-")]]
    return glyph.yMax - glyph.yMin


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    svg = ElementTree.parse(root / "art/belarusian_ruble.svg").getroot()
    path = svg.find("{http://www.w3.org/2000/svg}path")
    if path is None:
        raise ValueError("The Belarusian ruble SVG must contain a path")
    outline = path.attrib["d"]
    bounds = BoundsPen(None)
    parse_path(outline, bounds)
    if bounds.bounds is None:
        raise ValueError("The Belarusian ruble path is empty")
    x_min, y_min, _, y_max = bounds.bounds
    with TTFont(root / "app/src/main/res/font/golos_text_regular.ttf") as regular:
        regular_thickness = hyphen_thickness(regular)
    for font_path in sorted((root / "app/src/main/res/font").glob("golos_text_*.ttf")):
        with TTFont(font_path, recalcTimestamp=False) as font:
            repair_vertical_metrics(font)
            scale = font["OS/2"].sCapHeight / (y_max - y_min)
            bearing = round(font["head"].unitsPerEm * 0.05)
            shape = pathops.Path()
            transform = (scale, 0, 0, -scale, -x_min * scale, y_max * scale)
            parse_path(outline, TransformPen(shape.getPen(), transform))
            # Use the font's own stroke growth, preserving the official SVG at regular weight.
            stroke_width = hyphen_thickness(font) - regular_thickness
            if stroke_width > 0:
                stroke = pathops.Path(shape)
                stroke.stroke(
                    stroke_width,
                    pathops.LineCap.BUTT_CAP,
                    pathops.LineJoin.ROUND_JOIN,
                    4,
                )
                stroke.convertConicsToQuads()
                shape = pathops.op(shape, stroke, pathops.PathOp.UNION)
            left, bottom, right, top = shape.bounds
            fit = font["OS/2"].sCapHeight / (top - bottom)
            pen = TTGlyphPen(None)
            shape.draw(
                TransformPen(
                    Cu2QuPen(pen, max_err=0.5),
                    (fit, 0, 0, fit, bearing - left * fit, -bottom * fit),
                )
            )
            glyph = pen.glyph()
            order = font.getGlyphOrder()
            if GLYPH_NAME not in order:
                font.setGlyphOrder([*order, GLYPH_NAME])
            font["glyf"][GLYPH_NAME] = glyph
            font["hmtx"][GLYPH_NAME] = (
                round((right - left) * fit) + 2 * bearing,
                bearing,
            )
            for table in font["cmap"].tables:
                if table.isUnicode():
                    table.cmap[0x20C5] = GLYPH_NAME
            font.save(font_path)


if __name__ == "__main__":
    main()
