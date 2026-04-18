from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import inspect, text

db = SQLAlchemy()


def init_db(app):
    db.init_app(app)
    with app.app_context():
        db.create_all()
        _ensure_payment_created_at_column()


def _ensure_payment_created_at_column():
    inspector = inspect(db.engine)
    if not inspector.has_table("payments"):
        return

    columns = {column["name"] for column in inspector.get_columns("payments")}
    if "created_at" in columns:
        return

    db.session.execute(text("ALTER TABLE payments ADD COLUMN created_at TIMESTAMP"))
    db.session.execute(text("UPDATE payments SET created_at = NOW() WHERE created_at IS NULL"))
    db.session.execute(text("ALTER TABLE payments ALTER COLUMN created_at SET NOT NULL"))
    db.session.commit()
