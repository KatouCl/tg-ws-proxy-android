# Ярлыки управления прокси

После установки на Android 7.1+ долгое нажатие на иконку показывает `start_proxy`
(«Включить прокси») и `stop_proxy` («Выключить прокси»). Подписи локализованы
на русский и английский. В Samsung «Режимы и сценарии» выберите действие запуска
ярлыка приложения, если установленная версия One UI предоставляет его.

## Реализация

- `MainActivity` содержит метаданные `android.app.shortcuts`, ссылающиеся на
  `app/src/main/res/xml/shortcuts.xml`.
- App Shortcuts запускают Activity, поэтому их целью служит экспортированная
  `ProxyShortcutActivity` с темой `Theme.NoDisplay`, отключённым preview и анимацией.
  Она не создаёт интерфейс, отправляет явный broadcast и вызывает `finish()`
  в `onCreate`, до `onResume`. `MainActivity` при этом не запускается.
- Экспортированный `ProxyCommandReceiver` принимает только `ProxyService.ACTION_START`
  и `ACTION_STOP`. Сторонние extras игнорируются. `goAsync()` удерживает процесс
  на время чтения настроек; mutex сохраняет порядок запросов к контроллеру.
- START вызывает существующий `ProxyController.startFromSavedSettings()`, который
  читает DataStore и запускает `ProxyService` через `startForegroundService()`.
  Служба показывает уведомление и вызывает Rust через `NativeProxy.startProxy()`.
- STOP вызывает существующий `ProxyController.stop()`. Служба отменяет обновление
  статистики и перезапуск, вызывает `NativeProxy.stopProxy()`, ждёт завершения,
  освобождает WakeLock, снимает уведомление и вызывает `stopSelf()`.
  Возвращается `START_NOT_STICKY`, чтобы команда STOP не доставлялась заново.
- Вызовы native START/STOP синхронизированы; повторный START во время запуска
  не создаёт второй поток запуска.

`ProxyService` остаётся неэкспортированной. Публичный receiver позволяет любому
приложению на устройстве включать и выключать прокси с сохранённой конфигурацией.
Это намеренный интерфейс автоматизации без signature-permission, которая закрыла
бы доступ Samsung. Проект реализует локальный MTProto-прокси, а не Android VpnService.

## Ограничения платформы

Указать receiver/service непосредственно в `<shortcut><intent>` нельзя:
система запускает shortcut как Activity. NoDisplay не рисует окно приложения,
но факт запуска Activity остаётся; переходы лаунчера и поведение Samsung нужно
проверять на целевом устройстве. Гарантии отсутствия любых системных анимаций нет.

На Android 12+ запуск Foreground Service из фона требует системного исключения.
Вызов из автоматизации и NoDisplay сами по себе не гарантируют такое исключение.
При отказе ОС обработчик пишет ошибку в Logcat, не открывая интерфейс.
Исключение приложения из оптимизации батареи — один из предусмотренных Android
способов разрешить фоновый старт; настройки Samsung могут дополнительно влиять
на выполнение сценариев. Уведомление Foreground Service сохраняется обязательно;
его отображение в шторке зависит также от разрешения уведомлений.

Если автоматизатор умеет отправлять явные broadcasts, доступен путь вообще без Activity:

```sh
adb shell am broadcast \
  -n com.amurcanov.tgwsproxy/.ProxyCommandReceiver \
  -a com.amurcanov.tgwsproxy.START
adb shell am broadcast \
  -n com.amurcanov.tgwsproxy/.ProxyCommandReceiver \
  -a com.amurcanov.tgwsproxy.STOP
```

Эти команды проверяют endpoint, но привилегии adb shell отличаются от Samsung
автоматизации и не доказывают возможность фонового старта из обычного приложения.

## Проверка на устройстве

1. Установить APK и один раз настроить приложение. Проверить START/STOP через
   долгое нажатие на иконку и через Samsung-сценарий при открытом другом приложении.
2. Проверить работу при заблокированном экране и после выгрузки процесса.
3. При START должно появляться уведомление, а локальный порт — принимать соединения.
   Повторный START не должен создавать второй сервер.
4. При STOP уведомление и служба должны исчезать, порт — освобождаться.
   Проверить STOP во время запуска и повторный STOP после остановки.
5. В `adb logcat` проверить теги `ProxyCommandReceiver`, `ProxyService`,
   отсутствие `ForegroundServiceStartNotAllowedException` и запусков `MainActivity`.

Документация Android:
- https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
