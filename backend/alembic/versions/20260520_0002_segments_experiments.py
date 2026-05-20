"""segments + experiments

Revision ID: 20260520_0002
Revises: 20260520_0001
Create Date: 2026-05-20
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "20260520_0002"
down_revision = "20260520_0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # User segments — saved filter definitions used for targeted notifications.
    op.create_table(
        "user_segments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("name", sa.String(120), unique=True, nullable=False),
        sa.Column("description", sa.Text),
        sa.Column("filter", postgresql.JSONB, nullable=False),
        sa.Column("cached_user_count", sa.Integer),
        sa.Column("cached_at", sa.DateTime(timezone=True)),
        sa.Column("created_by", postgresql.UUID(as_uuid=True), sa.ForeignKey("admin_users.id", ondelete="SET NULL")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )

    # Experiments — A/B test config.
    op.create_table(
        "experiments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("key", sa.String(80), unique=True, nullable=False),
        sa.Column("name", sa.String(160), nullable=False),
        sa.Column("description", sa.Text),
        # status: draft, running, paused, completed
        sa.Column("status", sa.String(20), nullable=False, server_default="draft"),
        # variants: [{"key": "control", "weight": 50}, {"key": "treatment", "weight": 50}]
        sa.Column("variants", postgresql.JSONB, nullable=False),
        # Optional: scope to a segment id (NULL = all users)
        sa.Column(
            "segment_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("user_segments.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("started_at", sa.DateTime(timezone=True)),
        sa.Column("ended_at", sa.DateTime(timezone=True)),
        sa.Column("created_by", postgresql.UUID(as_uuid=True), sa.ForeignKey("admin_users.id", ondelete="SET NULL")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_experiments_status", "experiments", ["status"])

    # Per-user assignments — sticky after first computation so analytics are clean.
    op.create_table(
        "experiment_assignments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("experiment_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("experiments.id", ondelete="CASCADE"), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("variant", sa.String(80), nullable=False),
        sa.Column("assigned_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
        sa.Column("first_exposure_at", sa.DateTime(timezone=True)),
        sa.Column("converted_at", sa.DateTime(timezone=True)),
        sa.UniqueConstraint("experiment_id", "user_id", name="uq_assign_exp_user"),
    )
    op.create_index("ix_assignments_exp_variant", "experiment_assignments", ["experiment_id", "variant"])

    # Refunds — surface negative revenue in dashboard.
    op.create_table(
        "refunds",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("purchase_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("purchases.id", ondelete="CASCADE"), nullable=False),
        sa.Column("amount_micros", sa.Integer, nullable=False),
        sa.Column("currency", sa.String(8)),
        sa.Column("reason", sa.String(80)),
        sa.Column("notes", sa.Text),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()")),
    )
    op.create_index("ix_refunds_created", "refunds", ["created_at"])


def downgrade() -> None:
    op.drop_index("ix_refunds_created", table_name="refunds")
    op.drop_table("refunds")
    op.drop_index("ix_assignments_exp_variant", table_name="experiment_assignments")
    op.drop_table("experiment_assignments")
    op.drop_index("ix_experiments_status", table_name="experiments")
    op.drop_table("experiments")
    op.drop_table("user_segments")
