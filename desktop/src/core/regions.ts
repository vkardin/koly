import type { LocalizedName } from "./types";

/** Reference list used for demo data when the data service is unreachable. */
export const REGION_NAMES: Array<{ id: string; name: LocalizedName }> = [
  { id: "kyiv-city", name: { uk: "м. Київ", en: "Kyiv city", ru: "г. Киев" } },
  { id: "vinnytsia", name: { uk: "Вінницька", en: "Vinnytsia", ru: "Винницкая" } },
  { id: "volyn", name: { uk: "Волинська", en: "Volyn", ru: "Волынская" } },
  { id: "dnipropetrovsk", name: { uk: "Дніпропетровська", en: "Dnipropetrovsk", ru: "Днепропетровская" } },
  { id: "donetsk", name: { uk: "Донецька", en: "Donetsk", ru: "Донецкая" } },
  { id: "zhytomyr", name: { uk: "Житомирська", en: "Zhytomyr", ru: "Житомирская" } },
  { id: "zakarpattia", name: { uk: "Закарпатська", en: "Zakarpattia", ru: "Закарпатская" } },
  { id: "zaporizhzhia", name: { uk: "Запорізька", en: "Zaporizhzhia", ru: "Запорожская" } },
  { id: "ivano-frankivsk", name: { uk: "Івано-Франківська", en: "Ivano-Frankivsk", ru: "Ивано-Франковская" } },
  { id: "kyiv", name: { uk: "Київська", en: "Kyiv region", ru: "Киевская" } },
  { id: "kirovohrad", name: { uk: "Кіровоградська", en: "Kirovohrad", ru: "Кировоградская" } },
  { id: "lviv", name: { uk: "Львівська", en: "Lviv", ru: "Львовская" } },
  { id: "mykolaiv", name: { uk: "Миколаївська", en: "Mykolaiv", ru: "Николаевская" } },
  { id: "odesa", name: { uk: "Одеська", en: "Odesa", ru: "Одесская" } },
  { id: "poltava", name: { uk: "Полтавська", en: "Poltava", ru: "Полтавская" } },
  { id: "rivne", name: { uk: "Рівненська", en: "Rivne", ru: "Ровенская" } },
  { id: "sumy", name: { uk: "Сумська", en: "Sumy", ru: "Сумская" } },
  { id: "ternopil", name: { uk: "Тернопільська", en: "Ternopil", ru: "Тернопольская" } },
  { id: "kharkiv", name: { uk: "Харківська", en: "Kharkiv", ru: "Харьковская" } },
  { id: "kherson", name: { uk: "Херсонська", en: "Kherson", ru: "Херсонская" } },
  { id: "khmelnytskyi", name: { uk: "Хмельницька", en: "Khmelnytskyi", ru: "Хмельницкая" } },
  { id: "cherkasy", name: { uk: "Черкаська", en: "Cherkasy", ru: "Черкасская" } },
  { id: "chernivtsi", name: { uk: "Чернівецька", en: "Chernivtsi", ru: "Черновицкая" } },
  { id: "chernihiv", name: { uk: "Чернігівська", en: "Chernihiv", ru: "Черниговская" } },
];

export const DEMO_GROUPS = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"];
