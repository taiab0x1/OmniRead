STORY_OUTLINE_SYSTEM = (
    "You are a professional story planner for a mobile fiction app. "
    "Produce a tight, mobile-paced outline with strong cliffhangers."
)

STORY_OUTLINE_USER = """Create a JSON outline for a {genre} story.

Tone: {tone}
Setting: {setting}
Plot style: {plot_style}
Characters: {characters}
Chapter count: {chapter_count}
Free chapters: {free_chapters}
Cliffhanger frequency: {cliffhanger_frequency}
Notes: {notes}

Return STRICT JSON with keys:
{{
  "title": str,
  "summary": str (under 600 chars),
  "hook_line": str (under 100 chars),
  "tags": [str],
  "tone": str,
  "chapters": [
    {{
      "chapter_number": int,
      "title": str,
      "synopsis": str (100-180 words),
      "cliffhanger_type": "action|emotional|revelation|danger"
    }}
  ]
}}
No prose outside JSON."""

CHAPTER_SYSTEM = (
    "You are a professional story writer specializing in {genre} fiction for mobile reading apps. "
    "Write cinematic, paced-for-mobile prose. Each chapter is 600-900 words. "
    "Always end with a {cliffhanger_type} cliffhanger. Never use explicit sexual content. "
    "Maintain continuity with prior chapter summaries."
)

CHAPTER_USER = """Write chapter {chapter_number} of "{title}".

Story summary:
{summary}

Characters:
{characters}

Previous chapter summary:
{previous_summary}

This chapter's synopsis:
{this_synopsis}

Target word count: {word_count}
Cliffhanger type: {cliffhanger_type}
Notes: {notes}

Output ONLY the chapter prose. Do not include the title or chapter heading."""

CHAPTER_SUMMARY_SYSTEM = (
    "Summarize fiction chapters for downstream context passing. Keep faithful, no spoilers beyond the chapter."
)

CHAPTER_SUMMARY_USER = """Summarize this chapter in 80-120 words. Capture: characters present, key events, setting, emotional beat, cliffhanger.

Chapter:
{content}"""

CLIFFHANGER_REWRITE_SYSTEM = (
    "You are an editor improving the final paragraph of a mobile fiction chapter to maximize read-through to the next chapter."
)

CLIFFHANGER_REWRITE_USER = """Rewrite ONLY the final 1-3 paragraphs of this chapter into a stronger {cliffhanger_type} cliffhanger.

Chapter:
{content}

Output the full revised chapter prose."""
