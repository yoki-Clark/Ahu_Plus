import base64
import hashlib
import hmac
import json
from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True)
class CursorPosition:
    school_id: int
    create_time: datetime
    topic_id: int


def encode_cursor(position: CursorPosition, secret: str) -> str:
    payload = {
        "v": 1,
        "schoolId": position.school_id,
        "createTime": position.create_time.isoformat(timespec="seconds"),
        "topicId": position.topic_id,
    }
    body = base64.urlsafe_b64encode(
        json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    ).decode("ascii").rstrip("=")
    signature = hmac.new(secret.encode("utf-8"), body.encode("ascii"), hashlib.sha256).hexdigest()
    return f"{body}.{signature}"


def decode_cursor(token: str, secret: str) -> CursorPosition:
    try:
        body, signature = token.split(".", 1)
        expected = hmac.new(
            secret.encode("utf-8"), body.encode("ascii"), hashlib.sha256
        ).hexdigest()
        if not hmac.compare_digest(signature, expected):
            raise ValueError("invalid cursor signature")
        padded = body + "=" * (-len(body) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded).decode("utf-8"))
        if payload.get("v") != 1:
            raise ValueError("unsupported cursor version")
        school_id = int(payload["schoolId"])
        topic_id = int(payload["topicId"])
        create_time = datetime.fromisoformat(payload["createTime"])
        if school_id <= 0 or topic_id <= 0:
            raise ValueError("invalid cursor values")
        return CursorPosition(school_id, create_time, topic_id)
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        raise ValueError("invalid cursor") from exc
