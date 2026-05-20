import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { apiPost, extractApiError } from "@/lib/api";
import { FileUp, FlaskConical, Upload } from "lucide-react";

type ImportRow = {
  kind: "story" | "chapter";
  title?: string;
  genre?: string;
  summary?: string;
  hook_line?: string;
  tags?: string[];
  is_premium?: boolean;
  free_chapters?: number;
  story_slug?: string;
  chapter_number?: number;
  content?: string;
  is_free?: boolean;
  coin_cost?: number;
};

type ImportResult = {
  dry_run: boolean;
  summary: { stories_new: number; chapters_new: number; stories_existing: number };
  errors: { row: number; kind: string; error: string }[];
};

const SAMPLE = `[
  {
    "kind": "story",
    "title": "Velvet Dusk",
    "genre": "dark_romance",
    "summary": "A duke's secret, an heiress's revenge.",
    "tags": ["enemies-to-lovers", "regency"]
  },
  {
    "kind": "chapter",
    "story_slug": "velvet-dusk",
    "chapter_number": 1,
    "title": "The Masque",
    "content": "Eleanor stepped onto the lacquered floor…",
    "is_free": true
  }
]`;

export function ContentImportPage() {
  const [json, setJson] = useState(SAMPLE);
  const [parseError, setParseError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const dryRunMut = useMutation({
    mutationFn: (rows: ImportRow[]) =>
      apiPost<ImportResult>("/admin/content/import", { dry_run: true, rows }),
    onSuccess: (r) => setResult(r),
  });
  const commitMut = useMutation({
    mutationFn: (rows: ImportRow[]) =>
      apiPost<ImportResult>("/admin/content/import", { dry_run: false, rows }),
    onSuccess: (r) => setResult(r),
  });

  const parseAndRun = (commit: boolean) => {
    setParseError(null);
    let parsed: ImportRow[];
    try {
      const raw = JSON.parse(json);
      if (!Array.isArray(raw)) throw new Error("Expected a JSON array of rows.");
      parsed = raw as ImportRow[];
    } catch (e) {
      setParseError(e instanceof Error ? e.message : "Invalid JSON");
      return;
    }
    (commit ? commitMut : dryRunMut).mutate(parsed);
  };

  const handleFile = async (file: File) => {
    const text = await file.text();
    if (file.name.toLowerCase().endsWith(".csv")) {
      try {
        setJson(JSON.stringify(csvToRows(text), null, 2));
      } catch (e) {
        setParseError(e instanceof Error ? e.message : "Could not parse CSV");
      }
    } else {
      setJson(text);
    }
  };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Content Import</h1>
        <p className="text-sm text-ink-2">
          Bulk import stories + chapters. Dry run validates without writing. CSV is converted client-side to JSON.
        </p>
      </header>

      <section className="card p-4 flex items-center gap-3">
        <label className="btn-outline cursor-pointer">
          <FileUp size={14} />
          Load file (.json or .csv)
          <input
            type="file"
            accept=".json,.csv,application/json,text/csv"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void handleFile(f);
              if (e.target) (e.target as HTMLInputElement).value = "";
            }}
          />
        </label>
        <button
          className="btn-outline"
          disabled={dryRunMut.isPending}
          onClick={() => parseAndRun(false)}
        >
          <FlaskConical size={14} /> {dryRunMut.isPending ? "Validating…" : "Dry run"}
        </button>
        <button
          className="btn-primary"
          disabled={commitMut.isPending || (result ? result.errors.length > 0 : true)}
          title={result && result.errors.length > 0 ? "Fix errors first" : ""}
          onClick={() => {
            if (!confirm("Commit changes to the database?")) return;
            parseAndRun(true);
          }}
        >
          <Upload size={14} /> {commitMut.isPending ? "Importing…" : "Commit import"}
        </button>
      </section>

      <section className="card p-4">
        <div className="text-xs text-ink-3 mb-2">Rows (JSON array)</div>
        <textarea
          className="input font-mono text-xs min-h-[280px]"
          value={json}
          onChange={(e) => setJson(e.target.value)}
        />
        {parseError && <div className="text-danger text-xs mt-2">{parseError}</div>}
        {(dryRunMut.error || commitMut.error) && (
          <div className="text-danger text-xs mt-2">
            {extractApiError(dryRunMut.error || commitMut.error)}
          </div>
        )}
      </section>

      {result && (
        <section className="card p-4">
          <div className="font-display text-xl mb-2">
            {result.dry_run ? "Dry-run report" : "Import committed"}
          </div>
          <div className="grid grid-cols-3 gap-3">
            <Stat label="Stories (new)" value={result.summary.stories_new} />
            <Stat label="Chapters (new)" value={result.summary.chapters_new} />
            <Stat label="Stories (existing)" value={result.summary.stories_existing} />
          </div>
          {result.errors.length > 0 && (
            <div className="mt-3">
              <div className="text-sm text-danger mb-1">Errors ({result.errors.length})</div>
              <ul className="text-xs text-ink-2 space-y-1">
                {result.errors.slice(0, 50).map((e, i) => (
                  <li key={i} className="font-mono">
                    row {e.row} ({e.kind}): {e.error}
                  </li>
                ))}
                {result.errors.length > 50 && (
                  <li className="text-ink-3">… {result.errors.length - 50} more</li>
                )}
              </ul>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-bg-2 px-4 py-3">
      <div className="text-2xl text-ink-0 font-display">{value}</div>
      <div className="text-xs text-ink-3">{label}</div>
    </div>
  );
}

// Minimal CSV → row shape converter. Expects a header row with column names
// matching ImportRow fields. Tags is comma-then-pipe encoded ("a|b|c").
function csvToRows(text: string): ImportRow[] {
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length < 2) throw new Error("CSV needs a header row and at least one data row");
  const headers = splitCsvLine(lines[0]);
  const rows: ImportRow[] = [];
  for (let i = 1; i < lines.length; i++) {
    const cells = splitCsvLine(lines[i]);
    const obj: Record<string, unknown> = {};
    headers.forEach((h, idx) => {
      const raw = cells[idx];
      if (raw === undefined || raw === "") return;
      if (h === "tags") obj[h] = raw.split("|").map((t) => t.trim()).filter(Boolean);
      else if (h === "is_premium" || h === "is_free") obj[h] = raw === "true" || raw === "1";
      else if (h === "chapter_number" || h === "coin_cost" || h === "free_chapters") obj[h] = parseInt(raw, 10);
      else obj[h] = raw;
    });
    if (!obj.kind) throw new Error(`Row ${i} missing 'kind' column`);
    rows.push(obj as ImportRow);
  }
  return rows;
}

function splitCsvLine(line: string): string[] {
  const out: string[] = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cur += ch;
      }
    } else {
      if (ch === '"') inQuotes = true;
      else if (ch === ",") {
        out.push(cur);
        cur = "";
      } else cur += ch;
    }
  }
  out.push(cur);
  return out.map((s) => s.trim());
}
