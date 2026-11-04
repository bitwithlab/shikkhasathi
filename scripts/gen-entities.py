#!/usr/bin/env python3
"""
Generate JPA entities from the live Flyway schema (both databases), one class per table, into the owning module.

    python3 scripts/gen-entities.py            # needs psql + the two databases migrated
    PGURL_CORE=... PGURL_CONTENT=... python3 scripts/gen-entities.py

Column types, nullability and literal defaults come from information_schema, so entities never drift from
the migrations. Re-run after adding a Flyway migration; hand-written logic belongs in services, not here.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "backend/src/main/java/com/shikkhasathi"

# table -> module package (CORE / CONTENT ownership follows CLAUDE.md §0B)
MODULE = {
    "core": {
        "auth_otps": "auth", "sessions": "auth",
        "users": "user", "student_profiles": "student", "seller_profiles": "teacher",
        "classroom_announcements": "classroom", "classroom_courses": "classroom", "classroom_coursework": "classroom",
        "classroom_feed_items": "classroom", "classroom_materials": "classroom", "classroom_push_registrations": "classroom",
        "classroom_submissions": "classroom", "classroom_sync_runs": "classroom", "google_connections": "classroom",
        "notifications": "notification", "notification_preferences": "notification", "push_tokens": "notification", "email_logs": "notification",
        "orders": "payment", "order_items": "payment", "payments": "payment", "invoices": "payment", "refunds": "payment",
        "ledger_entries": "payment", "payouts": "payment",
        "coupons": "marketplace", "coupon_redemptions": "marketplace", "wishlists": "marketplace",
        "course_requests": "marketplace", "course_request_votes": "marketplace",
        "enrollments": "learning", "lesson_progress": "learning", "notes": "learning", "bookmarks": "learning",
        "questions": "learning", "answers": "learning", "reviews": "learning",
        "exam_plans": "exam", "flashcard_reviews": "exam", "mock_attempts": "exam", "mock_answers": "exam",
        "reports": "admin", "audit_logs": "common.audit",
        "integration_settings": "integration", "integration_tests": "integration", "settings": "integration",
    },
    "content": {
        "institutions": "catalog", "institution_requests": "catalog", "departments": "catalog", "programs": "catalog",
        "subject_masters": "catalog", "institution_subjects": "catalog",
        "courses": "course", "course_targets": "course", "course_sections": "course", "course_chapters": "course",
        "lessons": "course", "video_assets": "course", "documents": "course", "course_revisions": "course",
        "bundles": "course", "bundle_items": "course",
        "mock_tests": "exam", "mock_questions": "exam", "mock_options": "exam", "flashcards": "exam",
    },
}


def psql(db: str, sql: str) -> list[dict]:
    url = os.environ.get(f"PGURL_{db.upper()}") or f"postgresql://postgres:postgres@localhost:5432/{os.environ.get('PGDB_' + db.upper(), 'shikkhasathi_' + db)}"
    out = subprocess.run(["psql", url, "-tAc", f"select coalesce(json_agg(t), '[]') from ({sql}) t"], capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def camel(s: str, upper=False) -> str:
    p = s.split("_")
    r = p[0] + "".join(x.capitalize() for x in p[1:])
    return r[0].upper() + r[1:] if upper else r


SINGULAR = {"classroom_coursework": "ClassroomCoursework", "settings": "Setting", "classes": "Class", "notification_preferences": "NotificationPreference"}


def entity_name(table: str) -> str:
    if table in SINGULAR:
        return SINGULAR[table]
    words = table.split("_")
    last = words[-1]
    if last.endswith("ies"):
        last = last[:-3] + "y"
    elif last.endswith("sses"):
        last = last[:-2]
    elif last.endswith("s") and not last.endswith("ss"):
        last = last[:-1]
    return "".join(w.capitalize() for w in words[:-1] + [last])


TYPES = {
    "uuid": ("UUID", None), "text": ("String", None), "int4": ("Integer", None), "int8": ("Long", None),
    "bool": ("Boolean", None), "numeric": ("BigDecimal", None), "timestamptz": ("Instant", None),
    "jsonb": ("JsonNode", "SqlTypes.JSON"), "_text": ("List<String>", "SqlTypes.ARRAY"), "_uuid": ("List<UUID>", "SqlTypes.ARRAY"),
}


def default_literal(jtype: str, d: str | None) -> str | None:
    if d is None:
        return None
    m = re.fullmatch(r"'(.*)'::text", d)
    if m and jtype == "String":
        return '"' + m.group(1).replace('"', '\\"') + '"'
    if jtype == "Integer" and re.fullmatch(r"-?\d+", d):
        return d
    if jtype == "Long" and re.fullmatch(r"-?\d+", d):
        return d + "L"
    if jtype == "Boolean" and d in ("true", "false"):
        return d
    if jtype == "BigDecimal" and re.fullmatch(r"'?-?[\d.]+'?(::numeric)?", d):
        return f'new BigDecimal("{d.split("::")[0].strip(chr(39))}")'
    if jtype.startswith("List<") and d.startswith("'{}'"):
        return "new ArrayList<>()"
    m = re.fullmatch(r"ARRAY\[(.*)\]", d)
    if m and jtype == "List<String>":
        items = [x.split("::")[0].strip("'") for x in m.group(1).split(",")]
        return "new ArrayList<>(List.of(" + ", ".join(f'"{i}"' for i in items) + "))"
    if jtype == "JsonNode" and d.startswith("'[]'"):
        return "JsonNodeFactory.instance.arrayNode()"
    return None  # now() and friends are applied by @PrePersist / DB default (@DynamicInsert)


def generate(db: str):
    cols = psql(db, """select table_name, column_name, udt_name, is_nullable, column_default, ordinal_position
                       from information_schema.columns where table_schema='public' order by table_name, ordinal_position""")
    pks = psql(db, """select tc.table_name, kcu.column_name from information_schema.table_constraints tc
                      join information_schema.key_column_usage kcu on kcu.constraint_name=tc.constraint_name and kcu.table_name=tc.table_name
                      where tc.constraint_type='PRIMARY KEY' and tc.table_schema='public'""")
    pk = {r["table_name"]: r["column_name"] for r in pks}
    tables: dict[str, list[dict]] = {}
    for c in cols:
        tables.setdefault(c["table_name"], []).append(c)
    marker = "CoreEntity" if db == "core" else "ContentEntity"
    written = 0
    for table, cs in tables.items():
        module = MODULE[db].get(table)
        if not module:
            sys.exit(f"no module mapping for {db}.{table} — add it to MODULE in scripts/gen-entities.py")
        name = entity_name(table)
        pkg = f"com.shikkhasathi.{module}.entity"
        fields, imports = [], {"jakarta.persistence.*", "org.hibernate.annotations.DynamicInsert", f"com.shikkhasathi.config.db.{marker}"}
        has_created = any(c["column_name"] == "created_at" for c in cs)
        has_updated = any(c["column_name"] == "updated_at" for c in cs)
        for c in cs:
            jtype, jdbc = TYPES[c["udt_name"]]
            if "UUID" in jtype:
                imports.add("java.util.UUID")
            if jtype.startswith("List"):
                imports.update({"java.util.List", "java.util.ArrayList"})
            if jtype == "BigDecimal":
                imports.add("java.math.BigDecimal")
            if jtype == "Instant":
                imports.add("java.time.Instant")
            if jtype == "JsonNode":
                imports.update({"tools.jackson.databind.JsonNode", "tools.jackson.databind.node.JsonNodeFactory"})
            if jdbc:
                imports.update({"org.hibernate.annotations.JdbcTypeCode", "org.hibernate.type.SqlTypes"})
            ann = []
            if c["column_name"] == pk.get(table):
                ann.append("@Id")
            nn = c["is_nullable"] == "NO"
            col = f'@Column(name = "{c["column_name"]}"' + (", nullable = false" if nn else "") + ")"
            ann.append(col)
            if jdbc:
                ann.append(f"@JdbcTypeCode({jdbc})")
            init = default_literal(jtype, c["column_default"])
            fname = camel(c["column_name"])
            fields.append((ann, jtype, fname, init))
        lines = [f"package {pkg};", ""]
        lines += [f"import {i};" for i in sorted(imports)]
        lines += ["", f"/** Table <code>{table}</code> ({db.upper()} database). Generated by scripts/gen-entities.py — do not edit by hand. */",
                  "@Entity", f'@Table(name = "{table}")', f"@{marker}", "@DynamicInsert", f"public class {name} {{"]
        for ann, jtype, fname, init in fields:
            for a in ann:
                lines.append(f"  {a}")
            lines.append(f"  private {jtype} {fname}{' = ' + init if init else ''};")
            lines.append("")
        if has_created or has_updated or pk.get(table) == "id":
            lines.append("  @PrePersist")
            lines.append("  void prePersist() {")
            if pk.get(table) == "id" and any(c["column_name"] == "id" and c["udt_name"] == "uuid" for c in cs):
                lines.append("    if (id == null) id = com.shikkhasathi.common.Ids.uuidv7();")
            if has_created:
                lines.append("    if (createdAt == null) createdAt = Instant.now();")
            if has_updated:
                lines.append("    if (updatedAt == null) updatedAt = createdAt != null ? createdAt : Instant.now();")
            lines.append("  }")
            lines.append("")
        if has_updated:
            lines += ["  @PreUpdate", "  void preUpdate() {", "    updatedAt = Instant.now();", "  }", ""]
        for _, jtype, fname, _ in fields:
            cap = fname[0].upper() + fname[1:]
            lines.append(f"  public {jtype} get{cap}() {{ return {fname}; }}")
            lines.append(f"  public {name} set{cap}({jtype} v) {{ this.{fname} = v; return this; }}")
        lines.append("}")
        d = OUT / module.replace(".", "/") / "entity"
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{name}.java").write_text("\n".join(lines) + "\n")
        written += 1
    print(f"{db}: {written} entities")


if __name__ == "__main__":
    for db in ("core", "content"):
        generate(db)
