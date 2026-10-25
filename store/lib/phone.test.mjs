// node --test store/ — the drawn phone (phone.mjs).

import assert from "node:assert/strict";
import { test } from "node:test";
import { statusBar } from "./phone.mjs";

test("the status bar's colour runs over the capture's top edge, so no seam shows between them", () => {
  const bar = statusBar("#fbf8ff", "#1b1b21", "3:20");
  assert.match(bar, /box-shadow: 0 calc\(var\(--phone\) \* [.\d]+\) 0 #fbf8ff/);
});
