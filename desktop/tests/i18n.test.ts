import assert from "node:assert/strict";
import { test } from "node:test";
import { durationText } from "../src/core/i18n";

test("durationText spells out the countdown", () => {
  assert.equal(durationText(134), "2 год 14 хв");
  assert.equal(durationText(60), "1 год 0 хв");
  assert.equal(durationText(45), "45 хв");
});

test("durationText rounds up like the countdown", () => {
  assert.equal(durationText(0.2), "1 хв");
  assert.equal(durationText(59.5), "1 год 0 хв");
});
