/* Koly: copy the schedule from an open DTEK "shutdowns" page as a manual file.
   Build the bookmark with: python tools/build_bookmarklet.py
   Mirrors providers/_dtek_site.py: hour "h" covers [h-1:00, h:00). */
(function () {
  var REGIONS = {
    "dtek-dnem.com.ua": "dnipropetrovsk",
    "dtek-kem.com.ua": "kyiv-city",
    "dtek-krem.com.ua": "kyiv",
    "dtek-oem.com.ua": "odesa",
    "dtek-dem.com.ua": "donetsk"
  };
  var HALVES = {
    yes: [], no: [[0, 60, "off"]], first: [[0, 30, "off"]], second: [[30, 60, "off"]],
    maybe: [[0, 60, "maybe"]], mfirst: [[0, 30, "maybe"]], msecond: [[30, 60, "maybe"]]
  };
  var host = location.hostname.replace(/^www\./, "");
  var region = REGIONS[host];
  if (!region) { alert("Koly: open the DTEK shutdowns page first."); return; }
  var text = "";
  var scripts = document.scripts;
  for (var i = 0; i < scripts.length; i++) {
    if (scripts[i].textContent.indexOf("DisconSchedule.fact") >= 0) { text = scripts[i].textContent; break; }
  }
  var match = /DisconSchedule\.fact\s*=\s*/.exec(text);
  if (!match) { alert("Koly: no schedule on this page."); return; }
  var rest = text.slice(match.index + match[0].length), depth = 0, end = 0, inString = false;
  for (var k = 0; k < rest.length; k++) {
    var c = rest[k];
    if (inString) { if (c === "\\") k++; else if (c === "\"") inString = false; continue; }
    if (c === "\"") inString = true;
    else if (c === "{") depth++;
    else if (c === "}" && --depth === 0) { end = k + 1; break; }
  }
  var fact = JSON.parse(rest.slice(0, end));
  function hm(m) { return ("0" + Math.floor(m / 60)).slice(-2) + ":" + ("0" + (m % 60)).slice(-2); }
  function groupKey(name) { return name.split(".").map(function (p) { return ("000" + p).slice(-4); }).join("."); }
  function kyivDate(stamp) {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Kyiv" }).format(new Date(stamp * 1000));
  }
  var days = Object.keys(fact.data).sort(function (a, b) { return a - b; }).map(function (stamp) {
    var groups = {};
    Object.keys(fact.data[stamp]).filter(function (g) { return /^GPV/.test(g); })
      .sort(function (a, b) { return groupKey(a.slice(3)) < groupKey(b.slice(3)) ? -1 : 1; })
      .forEach(function (raw) {
        var hours = fact.data[stamp][raw], spans = [];
        for (var h = 1; h <= 24; h++) {
          var value = hours[String(h)];
          if (!(value in HALVES)) throw new Error("Koly: unknown value " + value + " in " + raw);
          HALVES[value].forEach(function (s) { spans.push([(h - 1) * 60 + s[0], (h - 1) * 60 + s[1], s[2]]); });
        }
        var merged = [];
        spans.forEach(function (s) {
          var last = merged[merged.length - 1];
          if (last && last[2] === s[2] && s[0] <= last[1]) last[1] = Math.max(last[1], s[1]);
          else merged.push(s.slice());
        });
        groups[raw.slice(3)] = merged.map(function (s) { return { from: hm(s[0]), to: hm(s[1]), type: s[2] }; });
      });
    return { date: kyivDate(+stamp), groups: groups };
  });
  var u = /(\d\d)\.(\d\d)\.(\d{4}) (\d\d):(\d\d)/.exec(fact.update || "");
  if (!u) { alert("Koly: the page has no update time."); return; }
  var offset = new Intl.DateTimeFormat("en", { timeZone: "Europe/Kyiv", timeZoneName: "longOffset" })
    .formatToParts(new Date(+u[3], +u[2] - 1, +u[1], +u[4])).filter(function (p) { return p.type === "timeZoneName"; })[0].value;
  var file = {
    sourceUrl: "https://www." + host + "/ua/shutdowns",
    updatedAt: u[3] + "-" + u[2] + "-" + u[1] + "T" + u[4] + ":" + u[5] + ":00" + (offset.replace("GMT", "") || "+00:00"),
    days: days
  };
  var json = JSON.stringify(file, null, 2) + "\n";
  var area = document.createElement("textarea");
  area.value = json;
  document.body.appendChild(area);
  area.select();
  var copied = document.execCommand("copy");
  document.body.removeChild(area);
  if (!copied) { prompt("Koly: copy this text", json); }
  window.open("https://github.com/vkardin/koly/edit/main/data/manual/" + region + ".json", "_blank");
  alert("Koly: schedule for " + days.map(function (d) { return d.date; }).join(", ") +
    " copied. On GitHub press Ctrl+A, Ctrl+V and \"Commit changes\".");
})();
