TG WS Proxy App v1.1

Приложение для управления Magisk-модулем [tg-ws-proxy](https://github.com/f1ndles/tg-ws-proxy) (ускорение Telegram через WebSocket + Cloudflare).

Требуется root (Magisk) и установленный модуль tg-ws-proxy.

Возможности:
- Статус демона (работает/остановлен, порт MTProto, CF домен)
- Запуск / остановка прокси одной кнопкой
- Кнопка "Применить в Telegram" — открывает tg://proxy-ссылку
- Настройки модуля: Автозапуск, CF Priority, CF Balance,
  Default Domains, CD Bypass, Auto TG, поле CF домена
- Просмотр лога

Технически: чистый Java (без зависимостей), управление через su
(вызывает action_web.sh модуля, правит config.conf через sed).
Сборка без Gradle: aapt2 + javac + d8 + apksigner.
