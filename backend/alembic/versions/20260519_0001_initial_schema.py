"""initial schema

Revision ID: 20260519_0001
Revises:
Create Date: 2026-05-19
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "20260519_0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")

    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("email", sa.String(255), unique=True),
        sa.Column("phone", sa.String(20), unique=True),
        sa.Column("username", sa.String(50), unique=True, nullable=False),
        sa.Column("password_hash", sa.String(255)),
        sa.Column("google_id", sa.String(100), unique=True),
        sa.Column("apple_id", sa.String(100), unique=True),
        sa.Column("avatar_url", sa.Text),
        sa.Column("is_guest", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_verified", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_banned", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("ban_reason", sa.Text),
        sa.Column("coin_balance", sa.Integer, nullable=False, server_default="0"),
        sa.Column("subscription_tier", sa.String(20), nullable=False, server_default="free"),
        sa.Column("subscription_expires_at", sa.DateTime(timezone=True)),
        sa.Column("reading_streak", sa.Integer, nullable=False, server_default="0"),
        sa.Column("last_read_at", sa.DateTime(timezone=True)),
        sa.Column("last_login_at", sa.DateTime(timezone=True)),
        sa.Column("failed_login_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("locked_until", sa.DateTime(timezone=True)),
        sa.Column("preferred_genres", postgresql.ARRAY(sa.String)),
        sa.Column("region", sa.String(8)),
        sa.Column("locale", sa.String(10), nullable=False, server_default="en"),
        sa.Column("birth_year", sa.Integer),
        sa.Column("age_verified_at", sa.DateTime(timezone=True)),
        sa.Column("deleted_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.CheckConstraint("coin_balance >= 0", name="ck_users_coin_balance_nonneg"),
    )
    op.create_index("ix_users_subscription", "users", ["subscription_tier", "subscription_expires_at"])
    op.create_index("ix_users_deleted_at", "users", ["deleted_at"])

    op.create_table(
        "refresh_tokens",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token_hash", sa.String(128), unique=True, nullable=False),
        sa.Column("parent_id", postgresql.UUID(as_uuid=True)),
        sa.Column("device_fingerprint", sa.String(255)),
        sa.Column("user_agent", sa.String(500)),
        sa.Column("ip_address", postgresql.INET),
        sa.Column("issued_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True)),
        sa.Column("revoked_reason", sa.String(50)),
    )
    op.create_index("ix_refresh_user_active", "refresh_tokens", ["user_id", "revoked_at", "expires_at"])

    op.create_table(
        "fcm_tokens",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token", sa.String(255), unique=True, nullable=False),
        sa.Column("platform", sa.String(20), nullable=False, server_default="android"),
        sa.Column("app_version", sa.String(20)),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "email_verifications",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token_hash", sa.String(128), unique=True, nullable=False),
        sa.Column("purpose", sa.String(30), nullable=False, server_default="email_verification"),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("used_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "stories",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("title", sa.String(255), nullable=False),
        sa.Column("slug", sa.String(255), unique=True, nullable=False),
        sa.Column("author_name", sa.String(100), nullable=False, server_default="AI Author"),
        sa.Column("cover_url", sa.Text),
        sa.Column("summary", sa.Text),
        sa.Column("hook_line", sa.String(500)),
        sa.Column("genre", sa.String(50), nullable=False),
        sa.Column("tags", postgresql.ARRAY(sa.String)),
        sa.Column("tone", sa.String(50)),
        sa.Column("age_rating", sa.String(8), nullable=False, server_default="13+"),
        sa.Column("status", sa.String(20), nullable=False, server_default="draft"),
        sa.Column("is_featured", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_trending", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_premium", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("total_chapters", sa.Integer, nullable=False, server_default="0"),
        sa.Column("free_chapters", sa.Integer, nullable=False, server_default="3"),
        sa.Column("view_count", sa.BigInteger, nullable=False, server_default="0"),
        sa.Column("like_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("bookmark_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("completion_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("avg_rating", sa.Numeric(3, 2), nullable=False, server_default="0"),
        sa.Column("total_ratings", sa.Integer, nullable=False, server_default="0"),
        sa.Column("estimated_read_time", sa.Integer),
        sa.Column("language", sa.String(10), nullable=False, server_default="en"),
        sa.Column("ai_generated", sa.Boolean, nullable=False, server_default=sa.text("true")),
        sa.Column("search_vector", postgresql.TSVECTOR),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("published_at", sa.DateTime(timezone=True)),
        sa.Column("scheduled_at", sa.DateTime(timezone=True)),
        sa.CheckConstraint("free_chapters >= 0", name="ck_stories_free_chapters_nonneg"),
        sa.CheckConstraint("avg_rating >= 0 AND avg_rating <= 5", name="ck_stories_rating_range"),
    )
    op.create_index("ix_stories_status_published", "stories", ["status", "published_at"])
    op.create_index("ix_stories_genre_published", "stories", ["genre", "published_at"])
    op.create_index("ix_stories_featured", "stories", ["is_featured", "published_at"])
    op.create_index("ix_stories_trending", "stories", ["is_trending", "published_at"])
    op.create_index("ix_stories_search", "stories", ["search_vector"], postgresql_using="gin")

    op.execute(
        """
        CREATE FUNCTION stories_tsvector_trigger() RETURNS trigger AS $$
        begin
          new.search_vector :=
            setweight(to_tsvector('english', coalesce(new.title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(new.hook_line, '')), 'B') ||
            setweight(to_tsvector('english', coalesce(new.summary, '')), 'C') ||
            setweight(to_tsvector('english', coalesce(array_to_string(new.tags, ' '), '')), 'C') ||
            setweight(to_tsvector('english', coalesce(new.genre, '')), 'D');
          return new;
        end
        $$ LANGUAGE plpgsql;
        """
    )
    op.execute(
        "CREATE TRIGGER stories_tsv_update BEFORE INSERT OR UPDATE ON stories "
        "FOR EACH ROW EXECUTE FUNCTION stories_tsvector_trigger();"
    )

    op.create_table(
        "bookmarks",
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_bookmarks_story", "bookmarks", ["story_id"])

    op.create_table(
        "story_likes",
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_story_likes_story", "story_likes", ["story_id"])

    op.create_table(
        "story_ratings",
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="CASCADE"), primary_key=True),
        sa.Column("rating", sa.Integer, nullable=False),
        sa.Column("review", sa.Text),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.CheckConstraint("rating BETWEEN 1 AND 5", name="ck_story_ratings_range"),
    )
    op.create_index("ix_story_ratings_story", "story_ratings", ["story_id"])

    op.create_table(
        "chapters",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="CASCADE"), nullable=False),
        sa.Column("chapter_number", sa.Integer, nullable=False),
        sa.Column("title", sa.String(255)),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("word_count", sa.Integer),
        sa.Column("is_free", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("coin_cost", sa.Integer, nullable=False, server_default="5"),
        sa.Column("has_cliffhanger", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("cliffhanger_preview", sa.Text),
        sa.Column("status", sa.String(20), nullable=False, server_default="draft"),
        sa.Column("published_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.UniqueConstraint("story_id", "chapter_number", name="uq_chapter_story_num"),
        sa.CheckConstraint("coin_cost >= 0", name="ck_chapter_coin_cost_nonneg"),
    )
    op.create_index("ix_chapters_story_num", "chapters", ["story_id", "chapter_number"])
    op.create_index("ix_chapters_status_published", "chapters", ["status", "published_at"])

    op.create_table(
        "user_chapter_unlocks",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("chapter_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("chapters.id", ondelete="CASCADE"), nullable=False),
        sa.Column("unlock_method", sa.String(20), nullable=False),
        sa.Column("coins_spent", sa.Integer, nullable=False, server_default="0"),
        sa.Column("unlocked_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.UniqueConstraint("user_id", "chapter_id", name="uq_unlock_user_chapter"),
    )
    op.create_index("ix_unlocks_user", "user_chapter_unlocks", ["user_id", "unlocked_at"])

    op.create_table(
        "reading_progress",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="CASCADE"), nullable=False),
        sa.Column("chapter_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("chapters.id", ondelete="CASCADE"), nullable=False),
        sa.Column("scroll_position", sa.Integer, nullable=False, server_default="0"),
        sa.Column("last_read_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("completed", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.UniqueConstraint("user_id", "story_id", name="uq_progress_user_story"),
    )
    op.create_index("ix_progress_user_recent", "reading_progress", ["user_id", "last_read_at"])

    op.create_table(
        "comments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("chapter_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("chapters.id", ondelete="CASCADE"), nullable=False),
        sa.Column("parent_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("comments.id", ondelete="CASCADE")),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("like_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("is_spoiler", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_hidden", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("moderation_status", sa.String(20), nullable=False, server_default="approved"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_comments_chapter", "comments", ["chapter_id", "created_at"])
    op.create_index("ix_comments_user", "comments", ["user_id", "created_at"])
    op.create_index("ix_comments_moderation", "comments", ["moderation_status"])

    op.create_table(
        "coin_transactions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("amount", sa.Integer, nullable=False),
        sa.Column("type", sa.String(40), nullable=False),
        sa.Column("description", sa.Text),
        sa.Column("reference_id", postgresql.UUID(as_uuid=True)),
        sa.Column("balance_after", sa.Integer, nullable=False),
        sa.Column("idempotency_key", sa.String(128), unique=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_coin_tx_user", "coin_transactions", ["user_id", "created_at"])
    op.create_index("ix_coin_tx_type", "coin_transactions", ["type", "created_at"])

    op.create_table(
        "rewarded_ad_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("device_fingerprint", sa.String(255)),
        sa.Column("ad_network", sa.String(50), nullable=False, server_default="admob"),
        sa.Column("ssv_transaction_id", sa.String(255), unique=True),
        sa.Column("reward_type", sa.String(30)),
        sa.Column("reward_amount", sa.Integer, nullable=False, server_default="0"),
        sa.Column("chapter_id", postgresql.UUID(as_uuid=True)),
        sa.Column("validated", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("ip_address", postgresql.INET),
        sa.Column("user_agent", sa.String(500)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_ad_events_user_day", "rewarded_ad_events", ["user_id", "created_at"])
    op.create_index("ix_ad_events_device_day", "rewarded_ad_events", ["device_fingerprint", "created_at"])

    op.create_table(
        "coin_packages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("sku", sa.String(80), unique=True, nullable=False),
        sa.Column("name", sa.String(80), nullable=False),
        sa.Column("coins", sa.Integer, nullable=False),
        sa.Column("bonus_coins", sa.Integer, nullable=False, server_default="0"),
        sa.Column("price_usd", sa.Numeric(10, 2), nullable=False),
        sa.Column("is_best_value", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.text("true")),
        sa.Column("sort_order", sa.Integer, nullable=False, server_default="0"),
    )

    op.create_table(
        "purchases",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("sku", sa.String(80), nullable=False),
        sa.Column("purchase_token", sa.String(512), unique=True, nullable=False),
        sa.Column("order_id", sa.String(120), unique=True),
        sa.Column("purchase_state", sa.String(30), nullable=False, server_default="purchased"),
        sa.Column("price_amount_micros", sa.BigInteger),
        sa.Column("price_currency", sa.String(8)),
        sa.Column("coins_credited", sa.Integer, nullable=False, server_default="0"),
        sa.Column("raw_response", postgresql.JSONB),
        sa.Column("verified_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_purchases_user", "purchases", ["user_id", "created_at"])

    op.create_table(
        "subscriptions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("sku", sa.String(80), nullable=False),
        sa.Column("purchase_token", sa.String(512), unique=True, nullable=False),
        sa.Column("state", sa.String(30), nullable=False, server_default="active"),
        sa.Column("auto_renewing", sa.Boolean, nullable=False, server_default=sa.text("true")),
        sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("cancelled_at", sa.DateTime(timezone=True)),
        sa.Column("grace_until", sa.DateTime(timezone=True)),
        sa.Column("raw_notification", postgresql.JSONB),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.UniqueConstraint("user_id", "sku", name="uq_sub_user_sku_active"),
    )
    op.create_index("ix_subscriptions_state_expires", "subscriptions", ["state", "expires_at"])

    op.create_table(
        "notifications",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("type", sa.String(50), nullable=False),
        sa.Column("title", sa.String(255)),
        sa.Column("body", sa.Text),
        sa.Column("data", postgresql.JSONB),
        sa.Column("is_read", sa.Boolean, nullable=False, server_default=sa.text("false")),
        sa.Column("sent_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_notifications_user_unread", "notifications", ["user_id", "is_read", "created_at"])

    op.create_table(
        "content_reports",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("reporter_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="SET NULL")),
        sa.Column("target_type", sa.String(20), nullable=False),
        sa.Column("target_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("reason", sa.String(40), nullable=False),
        sa.Column("notes", sa.Text),
        sa.Column("status", sa.String(20), nullable=False, server_default="pending"),
        sa.Column("resolved_by", postgresql.UUID(as_uuid=True)),
        sa.Column("resolved_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_reports_status", "content_reports", ["status", "created_at"])
    op.create_index("ix_reports_target", "content_reports", ["target_type", "target_id"])

    op.create_table(
        "admin_users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("email", sa.String(255), unique=True, nullable=False),
        sa.Column("password_hash", sa.String(255), nullable=False),
        sa.Column("name", sa.String(120)),
        sa.Column("role", sa.String(20), nullable=False, server_default="editor"),
        sa.Column("permissions", postgresql.JSONB, server_default=sa.text("'{}'::jsonb")),
        sa.Column("totp_secret", sa.String(120)),
        sa.Column("totp_enabled_at", sa.DateTime(timezone=True)),
        sa.Column("last_login_at", sa.DateTime(timezone=True)),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.text("true")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "audit_log",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("actor_admin_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("admin_users.id", ondelete="SET NULL")),
        sa.Column("actor_user_id", postgresql.UUID(as_uuid=True)),
        sa.Column("action", sa.String(60), nullable=False),
        sa.Column("target_type", sa.String(40)),
        sa.Column("target_id", sa.String(80)),
        sa.Column("metadata", postgresql.JSONB),
        sa.Column("ip_address", postgresql.INET),
        sa.Column("user_agent", sa.String(500)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_audit_actor_admin", "audit_log", ["actor_admin_id", "created_at"])
    op.create_index("ix_audit_action", "audit_log", ["action", "created_at"])
    op.create_index("ix_audit_target", "audit_log", ["target_type", "target_id"])

    op.create_table(
        "app_config",
        sa.Column("key", sa.String(100), primary_key=True),
        sa.Column("value", postgresql.JSONB, nullable=False),
        sa.Column("description", sa.Text),
        sa.Column("updated_by", postgresql.UUID(as_uuid=True), sa.ForeignKey("admin_users.id", ondelete="SET NULL")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    op.create_table(
        "ai_generation_jobs",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("job_type", sa.String(50), nullable=False),
        sa.Column("story_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("stories.id", ondelete="SET NULL")),
        sa.Column("chapter_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("chapters.id", ondelete="SET NULL")),
        sa.Column("prompt_template_id", postgresql.UUID(as_uuid=True)),
        sa.Column("provider", sa.String(40)),
        sa.Column("model", sa.String(80)),
        sa.Column("status", sa.String(20), nullable=False, server_default="pending"),
        sa.Column("input_params", postgresql.JSONB),
        sa.Column("output", postgresql.JSONB),
        sa.Column("error", sa.Text),
        sa.Column("tokens_in", sa.Integer),
        sa.Column("tokens_out", sa.Integer),
        sa.Column("cost_usd", sa.Numeric(10, 4)),
        sa.Column("requested_by", postgresql.UUID(as_uuid=True)),
        sa.Column("started_at", sa.DateTime(timezone=True)),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_ai_jobs_status", "ai_generation_jobs", ["status", "created_at"])
    op.create_index("ix_ai_jobs_type", "ai_generation_jobs", ["job_type", "created_at"])

    op.create_table(
        "prompt_templates",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("key", sa.String(80), nullable=False),
        sa.Column("version", sa.Integer, nullable=False, server_default="1"),
        sa.Column("purpose", sa.String(40), nullable=False),
        sa.Column("system_prompt", sa.Text, nullable=False),
        sa.Column("user_prompt", sa.Text, nullable=False),
        sa.Column("default_model", sa.String(80)),
        sa.Column("default_temperature", sa.Numeric(3, 2)),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.text("true")),
        sa.Column("notes", sa.Text),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.UniqueConstraint("key", "version", name="uq_prompt_key_version"),
    )


def downgrade() -> None:
    for table in [
        "prompt_templates",
        "ai_generation_jobs",
        "app_config",
        "audit_log",
        "admin_users",
        "content_reports",
        "notifications",
        "subscriptions",
        "purchases",
        "coin_packages",
        "rewarded_ad_events",
        "coin_transactions",
        "comments",
        "reading_progress",
        "user_chapter_unlocks",
        "chapters",
        "story_ratings",
        "story_likes",
        "bookmarks",
        "stories",
        "email_verifications",
        "fcm_tokens",
        "refresh_tokens",
        "users",
    ]:
        op.execute(f"DROP TABLE IF EXISTS {table} CASCADE")
    op.execute("DROP FUNCTION IF EXISTS stories_tsvector_trigger() CASCADE")
