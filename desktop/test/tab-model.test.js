import test from "node:test";
import assert from "node:assert/strict";
import {
  cycleTabId,
  indexOfTab,
  moveTab,
  neighbourIdOnClose,
  pushClosedTab,
  sessionTabs,
  takeClosedTab
} from "../src/tab-model.js";

function sampleTabs() {
  return [
    { id: 1, url: "https://a.example/", title: "A", zoom: 1 },
    { id: 2, url: "https://b.example/", title: "B", zoom: 1.25 },
    { id: 3, url: "https://c.example/", title: "C", zoom: 0.8 }
  ];
}

function ids(tabs) {
  return tabs.map(tab => tab.id);
}

test("indexOfTab reports positions and rejects unknown ids", () => {
  assert.equal(indexOfTab(sampleTabs(), 2), 1);
  assert.equal(indexOfTab(sampleTabs(), 99), -1);
  assert.equal(indexOfTab(sampleTabs(), null), -1);
});

test("a single tab has no neighbour and nothing to cycle to", () => {
  assert.equal(neighbourIdOnClose([{ id: 7 }], 7), null);
  assert.equal(cycleTabId([{ id: 7 }], 7, 1), null);
  assert.equal(cycleTabId([{ id: 7 }], 7, -1), null);
});

test("closing the last tab selects the previous tab", () => {
  assert.equal(neighbourIdOnClose(sampleTabs(), 3), 2);
});

test("closing a middle tab selects the tab that slides into its slot", () => {
  assert.equal(neighbourIdOnClose(sampleTabs(), 2), 3);
});

test("closing the first tab selects the new first tab", () => {
  assert.equal(neighbourIdOnClose(sampleTabs(), 1), 2);
});

test("closing a background tab follows the same neighbour rule", () => {
  const background = sampleTabs().map(tab => ({ ...tab, active: false }));
  assert.equal(neighbourIdOnClose(background, 2), 3);
  assert.equal(neighbourIdOnClose(background, 3), 2);
});

test("unknown ids never invent a neighbour or reorder tabs", () => {
  const tabs = sampleTabs();
  assert.equal(neighbourIdOnClose(tabs, 99), null);
  assert.equal(neighbourIdOnClose(tabs, null), null);
  assert.equal(cycleTabId(tabs, 99, 1), 1);
  assert.equal(cycleTabId(tabs, 99, -1), 3);
  assert.equal(moveTab(tabs, 99, 0), tabs);
});

test("cycle wraps at both ends", () => {
  assert.equal(cycleTabId(sampleTabs(), 3, 1), 1);
  assert.equal(cycleTabId(sampleTabs(), 1, -1), 3);
  assert.equal(cycleTabId(sampleTabs(), 1, 1), 2);
  assert.equal(cycleTabId(sampleTabs(), 2, -1), 1);
});

test("cycling needs at least two tabs", () => {
  assert.equal(cycleTabId([], null, 1), null);
  assert.equal(cycleTabId([], null, -1), null);
  assert.equal(cycleTabId([{ id: 1 }], 1, 1), null);
  assert.equal(cycleTabId([{ id: 1 }], 1, -1), null);
});

test("move clamps targets outside the tab range", () => {
  assert.deepEqual(ids(moveTab(sampleTabs(), 1, 99)), [2, 3, 1]);
  assert.deepEqual(ids(moveTab(sampleTabs(), 3, -5)), [3, 1, 2]);
  assert.deepEqual(ids(moveTab(sampleTabs(), 2, 1)), [1, 2, 3]);
  assert.deepEqual(ids(moveTab(sampleTabs(), 1, 0)), [1, 2, 3]);
  assert.deepEqual(ids(moveTab(sampleTabs(), 1, Number.NaN)), [1, 2, 3]);
});

test("closed tab stack keeps the newest entry first and honours the limit", () => {
  const first = pushClosedTab([], { url: "https://a.example/" }, 10);
  assert.deepEqual(first, [{ url: "https://a.example/" }]);

  const capped = pushClosedTab(first, { url: "https://b.example/" }, 1);
  assert.deepEqual(capped, [{ url: "https://b.example/" }]);

  const disabled = pushClosedTab(first, { url: "https://b.example/" }, 0);
  assert.deepEqual(disabled, []);

  const existing = [{ url: "https://a.example/" }];
  assert.deepEqual(pushClosedTab(existing, null, 5), existing);
  assert.deepEqual(pushClosedTab(null, { url: "https://c.example/" }, 3), [{ url: "https://c.example/" }]);
});

test("takeClosedTab pops the newest entry and reports an empty stack", () => {
  assert.deepEqual(takeClosedTab([]), { tab: null, rest: [] });
  assert.deepEqual(takeClosedTab(null), { tab: null, rest: [] });

  const stack = [{ url: "https://b.example/" }, { url: "https://a.example/" }];
  assert.deepEqual(takeClosedTab(stack), {
    tab: { url: "https://b.example/" },
    rest: [{ url: "https://a.example/" }]
  });
});

test("session tabs drop private tabs and keep the restorable shape", () => {
  const tabs = [
    { id: 1, url: "https://a.example/", title: "A", zoom: 1.25 },
    { id: 2, url: "https://secret.example/", title: "Secret", private: true, zoom: 1 },
    { id: 3, url: "https://c.example/", title: null, zoom: null },
    { id: 4, url: "https://d.example/", private: false },
    null
  ];

  assert.deepEqual(sessionTabs(tabs), [
    { url: "https://a.example/", title: "A", zoom: 1.25 },
    { url: "https://c.example/", title: "", zoom: 1 },
    { url: "https://d.example/", title: "", zoom: 1 }
  ]);
  assert.deepEqual(sessionTabs([{ url: "https://e.example/", title: 42, zoom: "2" }]), [
    { url: "https://e.example/", title: "", zoom: 2 }
  ]);
});

test("non-array inputs fall back to empty results", () => {
  assert.equal(indexOfTab(null, 1), -1);
  assert.equal(neighbourIdOnClose(undefined, 1), null);
  assert.equal(cycleTabId({}, 1, 1), null);
  assert.deepEqual(moveTab("tabs", 1, 0), []);
  assert.deepEqual(pushClosedTab(null, { url: "https://a.example/" }, 5), [{ url: "https://a.example/" }]);
  assert.deepEqual(takeClosedTab(42), { tab: null, rest: [] });
  assert.deepEqual(sessionTabs("tabs"), []);
});

test("operations never mutate their inputs", () => {
  const tabs = sampleTabs();
  const snapshot = JSON.stringify(tabs);
  const stack = [{ url: "https://b.example/" }];
  const stackSnapshot = JSON.stringify(stack);
  for (const tab of tabs) Object.freeze(tab);
  Object.freeze(tabs);
  Object.freeze(stack);

  assert.equal(neighbourIdOnClose(tabs, 2), 3);
  assert.equal(cycleTabId(tabs, 2, 1), 3);
  assert.notEqual(moveTab(tabs, 1, 2), tabs);
  assert.equal(moveTab(tabs, 99, 0), tabs);
  assert.notEqual(pushClosedTab(stack, { url: "https://c.example/" }, 5), stack);

  const taken = takeClosedTab(stack);
  assert.equal(taken.tab, stack[0]);
  assert.deepEqual(taken.rest, []);
  assert.deepEqual(ids(moveTab(tabs, 1, 3)), [2, 3, 1]);
  assert.deepEqual(sessionTabs(tabs).map(tab => tab.url), [
    "https://a.example/",
    "https://b.example/",
    "https://c.example/"
  ]);

  assert.equal(JSON.stringify(tabs), snapshot);
  assert.equal(JSON.stringify(stack), stackSnapshot);
});
