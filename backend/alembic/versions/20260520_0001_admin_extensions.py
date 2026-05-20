"""admin extensions: story display_order + admin comment replies

Revision ID: 20260520_0001
Revises: 20260519_0001
Create Date: 2026-05-20
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "20260520_0001"
down_revision = "20260519_0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Featured/trending ordering: lower = earlier in carousel
    op.add_column(
        "stories",
        sa.Column("display_order", sa.Integer, nullable=False, server_default="0"),
    )
    op.create_index(
        "ix_stories_featured_order",
        "stories",
        ["is_featured", "display_order"],
    )
    op.create_index(
        "ix_stories_trending_order",
        "stories",
        ["is_trending", "display_order"],
    )

    # Admin replies on comments — admin_id is the AdminUser who authored
    # the reply. When set, the comment is shown as an "Author" badge in app.
    op.add_column(
        "comments",
        sa.Column(
            "admin_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("admin_users.id", ondelete="SET NULL"),
            nullable=True,
        ),
    )
    # user_id is now nullable for admin-authored comments.
    op.alter_column("comments", "user_id", existing_type=postgresql.UUID(as_uuid=True), nullable=True)
    op.create_index("ix_comments_admin", "comments", ["admin_id"])


def downgrade() -> None:
    op.drop_index("ix_comments_admin", table_name="comments")
    op.alter_column("comments", "user_id", existing_type=postgresql.UUID(as_uuid=True), nullable=False)
    op.drop_column("comments", "admin_id")

    op.drop_index("ix_stories_trending_order", table_name="stories")
    op.drop_index("ix_stories_featured_order", table_name="stories")
    op.drop_column("stories", "display_order")
