import json
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID

from sqlalchemy.orm import Session

from app.models import AIGenerationJob, Chapter, Story
from app.services import ai_prompts, ai_provider
from app.utils.slug import slugify, unique_slug


def _est_cost_usd(provider: str, model: str, tokens_in: int, tokens_out: int) -> Decimal:
    rates = {
        "deepseek-chat": (Decimal("0.27") / 1_000_000, Decimal("1.10") / 1_000_000),
        "claude-sonnet-4-6": (Decimal("3.00") / 1_000_000, Decimal("15.00") / 1_000_000),
        "claude-opus-4-7": (Decimal("15.00") / 1_000_000, Decimal("75.00") / 1_000_000),
    }
    rin, rout = rates.get(model, (Decimal("0.50") / 1_000_000, Decimal("2.00") / 1_000_000))
    return (Decimal(tokens_in) * rin + Decimal(tokens_out) * rout).quantize(Decimal("0.0001"))


def generate_story_outline(
    db: Session,
    *,
    job: AIGenerationJob,
    params: dict,
    model: str | None = None,
    provider_name: str | None = None,
) -> dict:
    provider = ai_provider.get_provider(provider_name)
    user_prompt = ai_prompts.STORY_OUTLINE_USER.format(
        genre=params.get("genre", "drama"),
        tone=params.get("tone", "emotional, cinematic"),
        setting=params.get("setting", "contemporary urban"),
        plot_style=params.get("plot_style", "enemies_to_lovers"),
        characters=json.dumps(params.get("characters") or []),
        chapter_count=params.get("chapter_count", 12),
        free_chapters=params.get("free_chapters", 3),
        cliffhanger_frequency=params.get("cliffhanger_frequency", "every_chapter"),
        notes=params.get("notes") or "",
    )
    res = provider.chat(
        system=ai_prompts.STORY_OUTLINE_SYSTEM,
        user=user_prompt,
        model=model or "",
        max_tokens=2200,
        temperature=0.9,
    )
    job.provider = res.provider
    job.model = res.model
    job.tokens_in = res.tokens_in
    job.tokens_out = res.tokens_out
    job.cost_usd = _est_cost_usd(res.provider, res.model, res.tokens_in, res.tokens_out)
    job.completed_at = datetime.now(timezone.utc)

    text = res.text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        text = text.split("\n", 1)[1] if "\n" in text else text
        if text.endswith("```"):
            text = text[: -3]
    parsed = json.loads(text)
    return parsed


def materialize_outline(db: Session, *, outline: dict, params: dict) -> Story:
    title = outline.get("title", "Untitled")
    free_chapters = int(params.get("free_chapters", 3))
    base = slugify(title)
    from sqlalchemy import select

    def slug_taken(candidate: str) -> bool:
        return db.scalar(select(Story).where(Story.slug == candidate)) is not None

    slug = unique_slug(base, slug_taken)
    story = Story(
        title=title,
        slug=slug,
        author_name=params.get("author_name") or "AI Author",
        summary=outline.get("summary"),
        hook_line=outline.get("hook_line"),
        genre=params.get("genre", "drama"),
        tags=outline.get("tags") or [],
        tone=outline.get("tone") or params.get("tone"),
        free_chapters=free_chapters,
        status="draft",
        ai_generated=True,
    )
    db.add(story)
    db.flush()

    for idx, ch in enumerate(outline.get("chapters", []), start=1):
        chapter = Chapter(
            story_id=story.id,
            chapter_number=int(ch.get("chapter_number", idx)),
            title=ch.get("title"),
            content=f"[OUTLINE]\n{ch.get('synopsis', '')}",
            is_free=idx <= free_chapters,
            coin_cost=0 if idx <= free_chapters else 5,
            status="draft",
            has_cliffhanger=True,
            cliffhanger_preview=ch.get("synopsis", "")[:300],
        )
        db.add(chapter)
    story.total_chapters = len(outline.get("chapters", []))
    db.flush()
    return story


def generate_chapter_text(
    *,
    title: str,
    summary: str,
    characters: list[dict] | None,
    previous_summary: str | None,
    this_synopsis: str,
    chapter_number: int,
    word_count: int,
    cliffhanger_type: str,
    notes: str | None,
    genre: str,
    model: str | None = None,
    provider_name: str | None = None,
) -> ai_provider.AIResult:
    provider = ai_provider.get_provider(provider_name)
    sys = ai_prompts.CHAPTER_SYSTEM.format(genre=genre, cliffhanger_type=cliffhanger_type)
    user = ai_prompts.CHAPTER_USER.format(
        chapter_number=chapter_number,
        title=title,
        summary=summary,
        characters=json.dumps(characters or []),
        previous_summary=previous_summary or "(this is the first chapter)",
        this_synopsis=this_synopsis,
        word_count=word_count,
        cliffhanger_type=cliffhanger_type,
        notes=notes or "",
    )
    return provider.chat(system=sys, user=user, model=model or "", max_tokens=2000, temperature=0.9)
