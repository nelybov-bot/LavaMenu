## Архитектура

### `lavamenu/`
- Fabric mod (client-only)
- Keybind: `R` открывает основное меню.
- Настройки и быстрые действия хранятся локально (JSON конфиг).
- Команды отправляются как строки в чат (как будто пользователь ввёл вручную).

### Homes (LavaWin)
- Список: чат-ответ `/homes` → `HomesChatListener` → `HomesParser` (plain text + сессия).
- Rename: нет серверной атомарной команды → `HomeRenameSession` (подтверждение нового имени через `/homes` перед `/delhome`).
- Координаты точек на сервере отдаются в hover tooltip имени (не в видимой строке). Сейчас не парсятся; при необходимости — из `Component` / `hoverEvent`, не из `getString()`.

### Chats (ЛС)
- Парсер: `PmChatListener` + `PmParser` — строки `[PM] [HH:mm:ss] [вы >> nick]:` / `[nick >> вам]:`.
- Хранение: `ChatStore` → `config/lavamenu-chats.json` (без автоочистки; диалоги удаляет игрок).
- UI: вкладка «Чаты» (список как Друзья: overlay + hitArea + головы) + `ChatConversationScreen`; отправка `/msg <ник> <текст>`.
- Головы: `PlayerFaces` / `FaceCache` (`config/lavamenu/faces/`).
