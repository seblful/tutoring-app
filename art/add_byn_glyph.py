"""Embed the BYN SVG in the bundled fonts: uv run --with fonttools art/add_byn_glyph.py."""

from pathlib import Path
from xml.etree import ElementTree

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from fontTools.ttLib import TTFont


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
    x_min, y_min, x_max, y_max = bounds.bounds
    glyph_name = "uni20C5"
    for font_path in sorted((root / "app/src/main/res/font").glob("golos_text_*.ttf")):
        with TTFont(font_path, recalcTimestamp=False) as font:
            scale = font["OS/2"].sCapHeight / (y_max - y_min)
            bearing = round(font["head"].unitsPerEm * 0.05)
            pen = TTGlyphPen(None)
            transform = (scale, 0, 0, -scale, bearing - x_min * scale, y_max * scale)
            parse_path(outline, TransformPen(Cu2QuPen(pen, max_err=0.5), transform))
            glyph = pen.glyph()
            order = font.getGlyphOrder()
            if glyph_name not in order:
                font.setGlyphOrder([*order, glyph_name])
            font["glyf"][glyph_name] = glyph
            font["hmtx"][glyph_name] = (round((x_max - x_min) * scale) + 2 * bearing, bearing)
            for table in font["cmap"].tables:
                if table.isUnicode():
                    table.cmap[0x20C5] = glyph_name
            font.save(font_path)


if __name__ == "__main__":
    main()
