"""Region ids and localized names. Ids match the clients (Android, desktop)."""

from __future__ import annotations

from .models import LocalizedName

_REGIONS: list[tuple[str, str, str, str]] = [
    ("kyiv-city", "м. Київ", "Kyiv city", "г. Киев"),
    ("vinnytsia", "Вінницька", "Vinnytsia", "Винницкая"),
    ("volyn", "Волинська", "Volyn", "Волынская"),
    ("dnipropetrovsk", "Дніпропетровська", "Dnipropetrovsk", "Днепропетровская"),
    ("donetsk", "Донецька", "Donetsk", "Донецкая"),
    ("zhytomyr", "Житомирська", "Zhytomyr", "Житомирская"),
    ("zakarpattia", "Закарпатська", "Zakarpattia", "Закарпатская"),
    ("zaporizhzhia", "Запорізька", "Zaporizhzhia", "Запорожская"),
    ("ivano-frankivsk", "Івано-Франківська", "Ivano-Frankivsk", "Ивано-Франковская"),
    ("kyiv", "Київська", "Kyiv region", "Киевская"),
    ("kirovohrad", "Кіровоградська", "Kirovohrad", "Кировоградская"),
    ("lviv", "Львівська", "Lviv", "Львовская"),
    ("mykolaiv", "Миколаївська", "Mykolaiv", "Николаевская"),
    ("odesa", "Одеська", "Odesa", "Одесская"),
    ("poltava", "Полтавська", "Poltava", "Полтавская"),
    ("rivne", "Рівненська", "Rivne", "Ровенская"),
    ("sumy", "Сумська", "Sumy", "Сумская"),
    ("ternopil", "Тернопільська", "Ternopil", "Тернопольская"),
    ("kharkiv", "Харківська", "Kharkiv", "Харьковская"),
    ("kherson", "Херсонська", "Kherson", "Херсонская"),
    ("khmelnytskyi", "Хмельницька", "Khmelnytskyi", "Хмельницкая"),
    ("cherkasy", "Черкаська", "Cherkasy", "Черкасская"),
    ("chernivtsi", "Чернівецька", "Chernivtsi", "Черновицкая"),
    ("chernihiv", "Чернігівська", "Chernihiv", "Черниговская"),
]

REGION_NAMES: dict[str, LocalizedName] = {
    rid: LocalizedName(uk=uk, en=en, ru=ru) for rid, uk, en, ru in _REGIONS
}
REGION_ORDER: list[str] = [rid for rid, *_ in _REGIONS]
