(function () {
  "use strict";

  var API = "/api/v1/diagnostics";
  var PAGE_SIZE = 10;
  var INT16_MIN = -32768;
  var INT16_MAX = 32767;
  var DIAGNOSTIC_WINDOW_MS = 30000;
  var NOMINAL_ACCEL_LSB_PER_G = 16384;
  var NOMINAL_GYRO_LSB_PER_DPS = 131;
  var AXIS_COLORS = ["#7eb6ff", "#69d5a3", "#f5c36a"];

  var state = {
    devicePage: 0,
    sessionPage: 0,
    selectedDevice: null,
    selectedSession: null,
    sessionDetail: null,
    busy: false,
    diagnosticSamples: [],
    units: "nominal"
  };

  var els = {};

  document.addEventListener("DOMContentLoaded", init);

  function init() {
    cacheEls();
    bindEvents();
    setDiagnosticSamples(generateSyntheticSamples(120), "Vista previa TEST_DATA · no enviada");
    loadDevices();
  }

  function cacheEls() {
    els.formDevice = document.getElementById("form-device");
    els.deviceCode = document.getElementById("device-code");
    els.deviceDescription = document.getElementById("device-description");
    els.deviceFormError = document.getElementById("device-form-error");
    els.btnRegisterDevice = document.getElementById("btn-register-device");
    els.btnReloadDevices = document.getElementById("btn-reload-devices");
    els.devicesStatus = document.getElementById("devices-status");
    els.devicesList = document.getElementById("devices-list");
    els.devicesPager = document.getElementById("devices-pager");

    els.formSession = document.getElementById("form-session");
    els.sessionNotes = document.getElementById("session-notes");
    els.sessionFormError = document.getElementById("session-form-error");
    els.btnStartSession = document.getElementById("btn-start-session");
    els.btnReloadSessions = document.getElementById("btn-reload-sessions");
    els.selectedDeviceLabel = document.getElementById("selected-device-label");
    els.sessionsStatus = document.getElementById("sessions-status");
    els.sessionsList = document.getElementById("sessions-list");
    els.sessionsPager = document.getElementById("sessions-pager");

    els.selectedSessionLabel = document.getElementById("selected-session-label");
    els.btnComplete = document.getElementById("btn-complete");
    els.btnCancel = document.getElementById("btn-cancel");
    els.sessionActionMsg = document.getElementById("session-action-msg");
    els.syntheticBlock = document.getElementById("synthetic-block");
    els.btnSendSamples = document.getElementById("btn-send-samples");
    els.samplesMsg = document.getElementById("samples-msg");
    els.sessionDetail = document.getElementById("session-detail");
    els.testState = document.getElementById("test-state");
    els.sourceNote = document.getElementById("source-note");
    els.accChart = document.getElementById("acc-chart");
    els.gyroChart = document.getElementById("gyro-chart");
    els.accChartUnit = document.getElementById("acc-chart-unit");
    els.gyroChartUnit = document.getElementById("gyro-chart-unit");
    ["ax", "ay", "az", "gx", "gy", "gz"].forEach(function (axis) {
      els["metric" + axis.charAt(0).toUpperCase() + axis.slice(1)] = document.getElementById("metric-" + axis);
    });
    els.metricFrequency = document.getElementById("metric-frequency");
    els.metricSamples = document.getElementById("metric-samples");
    els.metricGaps = document.getElementById("metric-gaps");

    els.dialogRoot = document.getElementById("dialog-root");
    els.dialogTitle = document.getElementById("dialog-title");
    els.dialogBody = document.getElementById("dialog-body");
    els.dialogCancel = document.getElementById("dialog-cancel");
    els.dialogConfirm = document.getElementById("dialog-confirm");
  }

  function bindEvents() {
    els.formDevice.addEventListener("submit", onRegisterDevice);
    els.btnReloadDevices.addEventListener("click", function () {
      loadDevices(state.devicePage);
    });
    els.formSession.addEventListener("submit", onStartSession);
    els.btnReloadSessions.addEventListener("click", function () {
      if (state.selectedDevice) {
        loadSessions(state.sessionPage);
      }
    });
    els.btnComplete.addEventListener("click", onCompleteSession);
    els.btnCancel.addEventListener("click", onCancelSession);
    els.btnSendSamples.addEventListener("click", onPrepareSamples);
    Array.prototype.forEach.call(document.querySelectorAll("input[name='units']"), function (input) {
      input.addEventListener("change", function () {
        state.units = input.value === "raw" ? "raw" : "nominal";
        renderDiagnostics();
      });
    });
  }

  function onRegisterDevice(event) {
    event.preventDefault();
    hideMsg(els.deviceFormError);
    var code = els.deviceCode.value.trim();
    var description = els.deviceDescription.value.trim();
    if (!code) {
      showMsg(els.deviceFormError, "El código del dispositivo es obligatorio.");
      els.deviceCode.focus();
      return;
    }
    if (code.length > 64) {
      showMsg(els.deviceFormError, "deviceCode no puede superar 64 caracteres.");
      return;
    }
    if (description.length > 255) {
      showMsg(els.deviceFormError, "description no puede superar 255 caracteres.");
      return;
    }
    var body = { deviceCode: code };
    if (description) {
      body.description = description;
    }
    setBusy(true);
    api("POST", "/devices", body)
      .then(function () {
        els.formDevice.reset();
        setStatus(els.devicesStatus, "Dispositivo registrado.", "ok");
        return loadDevices(0);
      })
      .catch(function (err) {
        showMsg(els.deviceFormError, formatError(err));
      })
      .finally(function () {
        setBusy(false);
      });
  }

  function onStartSession(event) {
    event.preventDefault();
    hideMsg(els.sessionFormError);
    if (!state.selectedDevice) {
      showMsg(els.sessionFormError, "Seleccione un dispositivo.");
      return;
    }
    var notes = els.sessionNotes.value.trim();
    if (notes.length > 500) {
      showMsg(els.sessionFormError, "notes no puede superar 500 caracteres.");
      return;
    }
    var body = {};
    if (notes) {
      body.notes = notes;
    }
    setBusy(true);
    api("POST", "/devices/" + state.selectedDevice.id + "/sessions", body)
      .then(function (session) {
        els.sessionNotes.value = "";
        setStatus(els.sessionsStatus, "Sesión " + session.id + " iniciada con origen TEST_DATA.", "ok");
        return loadSessions(0).then(function () {
          return selectSession(session.id);
        });
      })
      .catch(function (err) {
        showMsg(els.sessionFormError, formatError(err));
      })
      .finally(function () {
        setBusy(false);
      });
  }

  function onCompleteSession() {
    if (!canMutateSelectedSession()) {
      return;
    }
    openDialog({
      title: "Completar sesión",
      bodyHtml:
        "<p>La sesión " + escapeHtml(String(state.selectedSession.id)) +
        " se marcará como COMPLETED.</p>" +
        "<p>Si hay muestras TEST_DATA, el resultado será <code>OBSERVED</code> " +
        "(solo se verificó el flujo de software). " +
        "Si no hay muestras, el resultado será <code>NOT_EVALUATED</code>. " +
        "Nunca se convertirá en PASSED.</p>",
      confirmLabel: "Completar",
      onConfirm: function () {
        setBusy(true);
        api("POST", "/sessions/" + state.selectedSession.id + "/complete")
          .then(function (session) {
            setStatus(
              els.sessionActionMsg,
              "Sesión completada: " + session.status + " / " + session.result + ".",
              "ok"
            );
            return refreshSelectedSession();
          })
          .catch(function (err) {
            setStatus(els.sessionActionMsg, formatError(err), "error");
          })
          .finally(function () {
            setBusy(false);
          });
      }
    });
  }

  function onCancelSession() {
    if (!canMutateSelectedSession()) {
      return;
    }
    openDialog({
      title: "Cancelar sesión",
      bodyHtml:
        "<p>Indique el motivo. Quedará en <code>cancellationReason</code> y no reemplaza las notas. " +
        "El resultado será <code>NOT_EVALUATED</code>.</p>" +
        "<div class=\"field\">" +
        "<label for=\"cancel-reason\">Motivo de cancelación</label>" +
        "<textarea id=\"cancel-reason\" maxlength=\"500\" rows=\"3\" required></textarea>" +
        "<p class=\"hint\">Obligatorio en esta interfaz. Máximo 500 caracteres.</p>" +
        "<p id=\"cancel-reason-error\" class=\"msg error\" hidden></p>" +
        "</div>",
      confirmLabel: "Cancelar sesión",
      danger: true,
      validate: function () {
        var input = document.getElementById("cancel-reason");
        var error = document.getElementById("cancel-reason-error");
        var reason = input.value.trim();
        if (!reason) {
          showMsg(error, "El motivo es obligatorio.");
          input.focus();
          return null;
        }
        if (reason.length > 500) {
          showMsg(error, "reason no puede superar 500 caracteres.");
          return null;
        }
        return reason;
      },
      onConfirm: function (reason) {
        setBusy(true);
        api("POST", "/sessions/" + state.selectedSession.id + "/cancel?reason=" + encodeURIComponent(reason))
          .then(function (session) {
            setStatus(
              els.sessionActionMsg,
              "Sesión cancelada: " + session.status + " / " + session.result + ".",
              "ok"
            );
            return refreshSelectedSession();
          })
          .catch(function (err) {
            setStatus(els.sessionActionMsg, formatError(err), "error");
          })
          .finally(function () {
            setBusy(false);
          });
      }
    });
  }

  function onPrepareSamples() {
    if (!canMutateSelectedSession()) {
      return;
    }
    var count = selectedSampleCount();
    var samples = generateSyntheticSamples(count);
    var preview = samples.slice(0, 3).map(function (s, i) {
      return "<li>#" + (i + 1) + " ax=" + s.axRaw + " ay=" + s.ayRaw + " az=" + s.azRaw +
        " gx=" + s.gxRaw + " gy=" + s.gyRaw + " gz=" + s.gzRaw + "</li>";
    }).join("");
    openDialog({
      title: "Enviar datos sintéticos",
      bodyHtml:
        "<p><strong>Datos sintéticos generados por la interfaz.</strong> " +
        "No son mediciones físicas reales. RAW int16 determinista, sin aleatoriedad.</p>" +
        "<p>Se enviarán <strong>" + count + "</strong> muestras TEST_DATA a la sesión " +
        escapeHtml(String(state.selectedSession.id)) + ".</p>" +
        "<p>Vista previa (primeras 3):</p><ul>" + preview + "</ul>",
      confirmLabel: "Enviar lote",
      onConfirm: function () {
        setBusy(true);
        api("POST", "/sessions/" + state.selectedSession.id + "/samples", { samples: samples })
          .then(function () {
            setDiagnosticSamples(samples, "Lote TEST_DATA preparado para la prueba " + state.selectedSession.id);
            setStatus(
              els.samplesMsg,
              "Lote sintético de " + count + " muestras enviado. Siguen siendo TEST_DATA, no mediciones reales.",
              "ok"
            );
            return refreshSelectedSession();
          })
          .catch(function (err) {
            setStatus(els.samplesMsg, formatError(err), "error");
          })
          .finally(function () {
            setBusy(false);
          });
      }
    });
  }

  function generateSyntheticSamples(count) {
    var samples = [];
    var i;
    for (i = 0; i < count; i += 1) {
      samples.push({
        axRaw: clampInt16(1024 + ((i * 37) % 401) - 200),
        ayRaw: clampInt16(512 + ((i * 53) % 301) - 150),
        azRaw: clampInt16(-256 + ((i * 41) % 201) - 100),
        gxRaw: clampInt16(300 + ((i * 29) % 121) - 60),
        gyRaw: clampInt16(-100 + ((i * 31) % 81) - 40),
        gzRaw: clampInt16(50 + ((i * 43) % 61) - 30)
      });
    }
    return samples;
  }

  function clampInt16(value) {
    if (value < INT16_MIN) {
      return INT16_MIN;
    }
    if (value > INT16_MAX) {
      return INT16_MAX;
    }
    return value;
  }

  function selectedSampleCount() {
    var checked = document.querySelector("input[name='sample-count']:checked");
    var value = checked ? Number(checked.value) : 10;
    if (value === 30 || value === 120) {
      return value;
    }
    return 10;
  }

  function setDiagnosticSamples(samples, label) {
    var cursor = 0;
    var telemetry = samples.map(function (sample, index) {
      if (index > 0) {
        cursor += 250;
        // Gap deterministic: the graph must not connect it as if data existed.
        if (index % 40 === 0) {
          cursor += 1000;
        }
      }
      return {
        timestamp: cursor,
        axRaw: sample.axRaw,
        ayRaw: sample.ayRaw,
        azRaw: sample.azRaw,
        gxRaw: sample.gxRaw,
        gyRaw: sample.gyRaw,
        gzRaw: sample.gzRaw
      };
    });
    var newest = telemetry.length ? telemetry[telemetry.length - 1].timestamp : 0;
    state.diagnosticSamples = telemetry.filter(function (sample) {
      return newest - sample.timestamp <= DIAGNOSTIC_WINDOW_MS;
    });
    els.testState.textContent = label;
    renderDiagnostics();
  }

  function renderDiagnostics() {
    var samples = state.diagnosticSamples;
    var last = samples.length ? samples[samples.length - 1] : null;
    renderAxisMetrics(last);
    var reception = readReception(samples);
    els.metricFrequency.textContent = reception.frequency + " Hz";
    els.metricSamples.textContent = String(samples.length);
    els.metricGaps.textContent = String(reception.gaps);
    els.sourceNote.textContent = samples.length + " muestras TEST_DATA en ventana visual de ~30 s · " + reception.gaps + " hueco(s).";
    var raw = state.units === "raw";
    els.accChartUnit.textContent = raw ? "int16 crudo" : "g nominal";
    els.gyroChartUnit.textContent = raw ? "int16 crudo" : "°/s nominal";
    drawImuChart(els.accChart, samples, ["axRaw", "ayRaw", "azRaw"], raw ? 1 : NOMINAL_ACCEL_LSB_PER_G, "ACC");
    drawImuChart(els.gyroChart, samples, ["gxRaw", "gyRaw", "gzRaw"], raw ? 1 : NOMINAL_GYRO_LSB_PER_DPS, "GYRO");
  }

  function renderAxisMetrics(sample) {
    ["ax", "ay", "az", "gx", "gy", "gz"].forEach(function (axis) {
      var element = els["metric" + axis.charAt(0).toUpperCase() + axis.slice(1)];
      if (!sample) {
        element.textContent = "—";
        return;
      }
      var raw = sample[axis + "Raw"];
      var divisor = axis.charAt(0) === "a" ? NOMINAL_ACCEL_LSB_PER_G : NOMINAL_GYRO_LSB_PER_DPS;
      var unit = axis.charAt(0) === "a" ? " g" : " °/s";
      element.textContent = state.units === "raw" ? raw + " raw" : (raw / divisor).toFixed(3) + unit;
    });
  }

  function readReception(samples) {
    if (samples.length < 2) {
      return { frequency: "—", gaps: 0 };
    }
    var span = samples[samples.length - 1].timestamp - samples[0].timestamp;
    var gaps = 0;
    samples.slice(1).forEach(function (sample, index) {
      if (sample.timestamp - samples[index].timestamp > 375) {
        gaps += 1;
      }
    });
    return { frequency: span > 0 ? ((samples.length - 1) * 1000 / span).toFixed(1) : "—", gaps: gaps };
  }

  function drawImuChart(canvas, samples, fields, divisor, label) {
    var context = canvas.getContext("2d");
    var width = canvas.width;
    var height = canvas.height;
    var padding = { left: 44, right: 12, top: 24, bottom: 25 };
    var newest = samples.length ? samples[samples.length - 1].timestamp : 0;
    var oldest = Math.max(0, newest - DIAGNOSTIC_WINDOW_MS);
    var values = [];
    samples.forEach(function (sample) {
      fields.forEach(function (field) { values.push(sample[field] / divisor); });
    });
    var minimum = values.length ? Math.min.apply(null, values) : -1;
    var maximum = values.length ? Math.max.apply(null, values) : 1;
    var range = Math.max(0.1, maximum - minimum);
    minimum -= range * 0.15;
    maximum += range * 0.15;
    context.clearRect(0, 0, width, height);
    context.fillStyle = "#0b1016";
    context.fillRect(0, 0, width, height);
    context.strokeStyle = "#273545";
    context.lineWidth = 1;
    for (var row = 0; row <= 4; row += 1) {
      var y = padding.top + (height - padding.top - padding.bottom) * row / 4;
      context.beginPath(); context.moveTo(padding.left, y); context.lineTo(width - padding.right, y); context.stroke();
    }
    context.fillStyle = "#b7c3d0";
    context.font = "12px Segoe UI";
    context.fillText(maximum.toFixed(2), 2, padding.top + 4);
    context.fillText(minimum.toFixed(2), 2, height - padding.bottom + 4);
    context.fillText("-30 s", padding.left, height - 7);
    context.fillText("ahora", width - padding.right - 34, height - 7);
    context.fillStyle = "#f2f5f8";
    context.fillText(label + " · " + (state.units === "raw" ? "int16" : "nominal"), padding.left, 14);
    fields.forEach(function (field, fieldIndex) {
      context.strokeStyle = AXIS_COLORS[fieldIndex];
      context.lineWidth = 2;
      var drawing = false;
      samples.forEach(function (sample, index) {
        var previous = index ? samples[index - 1] : null;
        var hasGap = previous && sample.timestamp - previous.timestamp > 375;
        var x = padding.left + (sample.timestamp - oldest) / DIAGNOSTIC_WINDOW_MS * (width - padding.left - padding.right);
        var y = padding.top + (maximum - sample[field] / divisor) / (maximum - minimum) * (height - padding.top - padding.bottom);
        if (!drawing || hasGap) {
          context.beginPath();
          context.moveTo(x, y);
          drawing = true;
        } else {
          context.lineTo(x, y);
          context.stroke();
        }
      });
    });
    fields.forEach(function (field, index) {
      var x = width - padding.right - 125 + index * 42;
      context.fillStyle = AXIS_COLORS[index];
      context.fillRect(x, 8, 10, 3);
      context.fillStyle = "#b7c3d0";
      context.fillText(field.replace("Raw", "").toUpperCase().slice(-1), x + 14, 14);
    });
  }

  function loadDevices(page) {
    var number = page == null ? 0 : page;
    state.devicePage = number;
    setStatus(els.devicesStatus, "Cargando dispositivos…");
    els.devicesList.innerHTML = "";
    els.devicesPager.innerHTML = "";
    return api("GET", "/devices?page=" + number + "&size=" + PAGE_SIZE)
      .then(function (data) {
        var pageInfo = readPage(data);
        var items = data.content || [];
        if (items.length === 0) {
          setStatus(els.devicesStatus, "No hay dispositivos registrados.");
          els.devicesList.innerHTML = "<p class=\"empty\">Estado vacío: registre un dispositivo para comenzar.</p>";
          return;
        }
        setStatus(els.devicesStatus, pageInfo.totalElements + " dispositivo(s). Página " +
          (pageInfo.number + 1) + " de " + Math.max(pageInfo.totalPages, 1) + ".");
        items.forEach(function (device) {
          els.devicesList.appendChild(renderDeviceItem(device));
        });
        renderPager(els.devicesPager, pageInfo, loadDevices);
      })
      .catch(function (err) {
        setStatus(els.devicesStatus, formatError(err), "error");
      });
  }

  function renderDeviceItem(device) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "item";
    btn.setAttribute("role", "listitem");
    btn.setAttribute("data-id", String(device.id));
    if (state.selectedDevice && state.selectedDevice.id === device.id) {
      btn.classList.add("selected");
      btn.setAttribute("aria-current", "true");
    }
    btn.innerHTML =
      "<strong>" + escapeHtml(device.deviceCode || "") + "</strong>" +
      "<span>" + escapeHtml(device.description || "Sin descripción") + "</span>" +
      "<span class=\"item-meta\">Registro: " + formatDate(device.registeredAt) + "</span>";
    btn.addEventListener("click", function () {
      selectDevice(device);
    });
    return btn;
  }

  function selectDevice(device) {
    state.selectedDevice = device;
    state.selectedSession = null;
    state.sessionDetail = null;
    state.sessionPage = 0;
    els.selectedDeviceLabel.textContent =
      "Dispositivo seleccionado: " + device.deviceCode + " (id " + device.id + ").";
    els.btnStartSession.disabled = state.busy;
    els.btnReloadSessions.disabled = state.busy;
    clearSessionDetail();
    highlightSelected("devices-list", device.id);
    loadSessions(0);
  }

  function loadSessions(page) {
    if (!state.selectedDevice) {
      return Promise.resolve();
    }
    var number = page == null ? 0 : page;
    state.sessionPage = number;
    setStatus(els.sessionsStatus, "Cargando historial…");
    els.sessionsList.innerHTML = "";
    els.sessionsPager.innerHTML = "";
    return api("GET", "/devices/" + state.selectedDevice.id + "/sessions?page=" + number + "&size=" + PAGE_SIZE)
      .then(function (data) {
        var pageInfo = readPage(data);
        var items = data.content || [];
        if (items.length === 0) {
          setStatus(els.sessionsStatus, "No hay sesiones para este dispositivo.");
          els.sessionsList.innerHTML = "<p class=\"empty\">Estado vacío: inicie una sesión TEST_DATA.</p>";
          return;
        }
        setStatus(els.sessionsStatus, pageInfo.totalElements + " sesión(es). Página " +
          (pageInfo.number + 1) + " de " + Math.max(pageInfo.totalPages, 1) + ".");
        items.forEach(function (session) {
          els.sessionsList.appendChild(renderSessionItem(session));
        });
        renderPager(els.sessionsPager, pageInfo, loadSessions);
      })
      .catch(function (err) {
        setStatus(els.sessionsStatus, formatError(err), "error");
      });
  }

  function renderSessionItem(session) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "item";
    btn.setAttribute("role", "listitem");
    btn.setAttribute("data-id", String(session.id));
    if (state.selectedSession && state.selectedSession.id === session.id) {
      btn.classList.add("selected");
      btn.setAttribute("aria-current", "true");
    }
    btn.innerHTML =
      "<strong>Sesión " + escapeHtml(String(session.id)) + "</strong>" +
      "<span>" +
      badge("status-" + session.status, session.status) +
      badge("result-" + session.result, session.result) +
      badge("origin-" + session.dataOrigin, session.dataOrigin) +
      "</span>" +
      "<span class=\"item-meta\">Inicio: " + formatDate(session.startedAt) +
      " · Fin: " + formatDate(session.finishedAt) + "</span>";
    btn.addEventListener("click", function () {
      selectSession(session.id);
    });
    return btn;
  }

  function selectSession(sessionId) {
    setStatus(els.sessionActionMsg, "Consultando sesión…");
    return api("GET", "/sessions/" + sessionId)
      .then(function (detail) {
        state.selectedSession = detail;
        state.sessionDetail = detail;
        els.selectedSessionLabel.textContent = "Sesión " + detail.id + " seleccionada.";
        renderSessionDetail(detail);
        updateSessionActions();
        highlightSelected("sessions-list", detail.id);
        setStatus(els.sessionActionMsg, "");
      })
      .catch(function (err) {
        setStatus(els.sessionActionMsg, formatError(err), "error");
      });
  }

  function refreshSelectedSession() {
    var id = state.selectedSession && state.selectedSession.id;
    return loadSessions(state.sessionPage).then(function () {
      if (id) {
        return selectSession(id);
      }
      return null;
    });
  }

  function renderSessionDetail(detail) {
    var summary = detail.summary;
    var sampleCount = summary ? summary.sampleCount : 0;
    var html = "<dl class=\"cards\">" +
      card("Estado", detail.status) +
      card("Resultado", detail.result) +
      card("Origen", detail.dataOrigin) +
      card("Muestras", String(sampleCount)) +
      card("Inicio", formatDate(detail.startedAt)) +
      card("Fin", formatDate(detail.finishedAt)) +
      card("Notas", detail.notes || "—") +
      card("resultNote", detail.resultNote || "—") +
      card("Motivo de cancelación", detail.cancellationReason || "—") +
      "</dl>";

    if (detail.result === "OBSERVED") {
      html += "<p class=\"warning\">OBSERVED: solo se verificó el flujo de software con TEST_DATA. " +
        "No representa una medición física real y no es PASSED.</p>";
    }
    if (detail.result === "NOT_EVALUATED") {
      html += "<p>NOT_EVALUATED: no hay evaluación técnica (sesión cancelada o sin muestras suficientes).</p>";
    }

    if (summary) {
      html += "<h3>Estadísticas RAW (no son mediciones físicas reales)</h3>";
      html += "<div class=\"table-wrap\"><table><thead><tr>" +
        "<th>Eje</th><th>Mín</th><th>Promedio</th><th>Máx</th><th>Desv. estándar</th>" +
        "</tr></thead><tbody>" +
        axisRow("AX", summary.ax) +
        axisRow("AY", summary.ay) +
        axisRow("AZ", summary.az) +
        axisRow("GX", summary.gx) +
        axisRow("GY", summary.gy) +
        axisRow("GZ", summary.gz) +
        "</tbody></table></div>";
      html += "<p class=\"hint\">Barras simples de amplitud relativa (máximo absoluto del eje frente a 32767). " +
        "La API no devuelve muestras individuales: las gráficas principales usan solo el búfer local TEST_DATA.</p>";
      html += axisBars("AX", summary.ax) +
        axisBars("AY", summary.ay) +
        axisBars("AZ", summary.az) +
        axisBars("GX", summary.gx) +
        axisBars("GY", summary.gy) +
        axisBars("GZ", summary.gz);
      if (summary.computedAt) {
        html += "<p class=\"hint\">Calculado: " + formatDate(summary.computedAt) + "</p>";
      }
    } else {
      html += "<p class=\"empty\">No hay resumen estadístico. Completar sin muestras deja NOT_EVALUATED y no crea summary.</p>";
    }

    els.sessionDetail.innerHTML = html;
  }

  function card(label, value) {
    return "<div class=\"card\"><dt>" + escapeHtml(label) + "</dt><dd>" +
      escapeHtml(value == null ? "—" : String(value)) + "</dd></div>";
  }

  function axisRow(name, stats) {
    if (!stats) {
      return "";
    }
    return "<tr><th>" + name + "</th><td>" + fmtNum(stats.min) + "</td><td>" +
      fmtNum(stats.avg) + "</td><td>" + fmtNum(stats.max) + "</td><td>" +
      fmtNum(stats.std) + "</td></tr>";
  }

  function axisBars(name, stats) {
    if (!stats) {
      return "";
    }
    var pct = Math.min(100, Math.round((Math.abs(Number(stats.max) || 0) / 32767) * 100));
    return "<div class=\"bar-row\"><span>" + name + "</span><div class=\"bar\" aria-hidden=\"true\">" +
      "<span style=\"width:" + pct + "%\"></span></div><span>" + pct + "% máx</span></div>";
  }

  function updateSessionActions() {
    var inProgress = canMutateSelectedSession();
    els.btnComplete.disabled = !inProgress || state.busy;
    els.btnCancel.disabled = !inProgress || state.busy;
    els.syntheticBlock.hidden = !inProgress;
    els.btnSendSamples.disabled = !inProgress || state.busy;
  }

  function canMutateSelectedSession() {
    return !!(state.selectedSession && state.selectedSession.status === "IN_PROGRESS");
  }

  function clearSessionDetail() {
    els.selectedSessionLabel.textContent = "Seleccione una sesión para ver el resumen técnico.";
    els.sessionDetail.innerHTML = "";
    els.syntheticBlock.hidden = true;
    els.btnComplete.disabled = true;
    els.btnCancel.disabled = true;
    setStatus(els.sessionActionMsg, "");
    setStatus(els.samplesMsg, "");
  }

  function highlightSelected(listId, id) {
    var list = document.getElementById(listId);
    Array.prototype.forEach.call(list.querySelectorAll(".item"), function (item) {
      var selected = item.getAttribute("data-id") === String(id);
      item.classList.toggle("selected", selected);
      if (selected) {
        item.setAttribute("aria-current", "true");
      } else {
        item.removeAttribute("aria-current");
      }
    });
  }

  function renderPager(container, pageInfo, onChange) {
    container.innerHTML = "";
    var prev = document.createElement("button");
    prev.type = "button";
    prev.className = "btn btn-secondary";
    prev.textContent = "Anterior";
    prev.disabled = pageInfo.number <= 0 || state.busy;
    prev.addEventListener("click", function () {
      onChange(pageInfo.number - 1);
    });
    var next = document.createElement("button");
    next.type = "button";
    next.className = "btn btn-secondary";
    next.textContent = "Siguiente";
    next.disabled = pageInfo.number + 1 >= pageInfo.totalPages || state.busy;
    next.addEventListener("click", function () {
      onChange(pageInfo.number + 1);
    });
    var label = document.createElement("span");
    label.className = "muted";
    label.textContent = "Página " + (pageInfo.number + 1) + " / " + Math.max(pageInfo.totalPages, 1);
    container.appendChild(prev);
    container.appendChild(label);
    container.appendChild(next);
  }

  function readPage(data) {
    var page = data.page || {};
    return {
      size: page.size != null ? page.size : PAGE_SIZE,
      number: page.number != null ? page.number : 0,
      totalElements: page.totalElements != null ? page.totalElements : (data.content ? data.content.length : 0),
      totalPages: page.totalPages != null ? page.totalPages : 1
    };
  }

  function api(method, path, body) {
    var options = {
      method: method,
      headers: { Accept: "application/json" }
    };
    if (body !== undefined) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(body);
    }
    return fetch(API + path, options).then(function (response) {
      return parseJsonSafe(response).then(function (payload) {
        if (!response.ok) {
          var error = new Error(payload && payload.message ? payload.message : "Error HTTP " + response.status);
          error.payload = payload;
          error.status = response.status;
          throw error;
        }
        return payload;
      });
    });
  }

  function parseJsonSafe(response) {
    return response.text().then(function (text) {
      if (!text) {
        return {};
      }
      try {
        return JSON.parse(text);
      } catch (e) {
        return { message: text };
      }
    });
  }

  function formatError(err) {
    if (!err) {
      return "Error inesperado.";
    }
    var payload = err.payload;
    if (payload && payload.fields) {
      var fields = Object.keys(payload.fields).map(function (key) {
        return key + ": " + payload.fields[key];
      }).join(". ");
      return (payload.message || "Validación") + (fields ? " — " + fields : "");
    }
    if (payload && payload.code) {
      return payload.code + ": " + (payload.message || err.message);
    }
    return err.message || "No se pudo completar la operación.";
  }

  function openDialog(options) {
    els.dialogTitle.textContent = options.title;
    els.dialogBody.innerHTML = options.bodyHtml;
    els.dialogConfirm.textContent = options.confirmLabel || "Confirmar";
    els.dialogConfirm.className = options.danger ? "btn btn-danger" : "btn btn-primary";
    els.dialogRoot.hidden = false;

    var previouslyFocused = document.activeElement;
    var onKey = function (event) {
      if (event.key === "Escape") {
        close();
      }
    };
    var close = function () {
      els.dialogRoot.hidden = true;
      document.removeEventListener("keydown", onKey);
      els.dialogCancel.onclick = null;
      els.dialogConfirm.onclick = null;
      if (previouslyFocused && previouslyFocused.focus) {
        previouslyFocused.focus();
      }
    };

    els.dialogCancel.onclick = close;
    els.dialogConfirm.onclick = function () {
      var value = true;
      if (options.validate) {
        value = options.validate();
        if (value == null) {
          return;
        }
      }
      close();
      options.onConfirm(value);
    };
    document.addEventListener("keydown", onKey);
    var firstField = els.dialogBody.querySelector("textarea, input, button");
    (firstField || els.dialogConfirm).focus();
  }

  function setBusy(busy) {
    state.busy = busy;
    els.btnRegisterDevice.disabled = busy;
    els.btnReloadDevices.disabled = busy;
    els.btnStartSession.disabled = busy || !state.selectedDevice;
    els.btnReloadSessions.disabled = busy || !state.selectedDevice;
    updateSessionActions();
  }

  function setStatus(el, text, kind) {
    el.textContent = text || "";
    el.className = "status-line" + (kind ? " " + kind : "");
  }

  function showMsg(el, text) {
    el.hidden = false;
    el.textContent = text;
  }

  function hideMsg(el) {
    el.hidden = true;
    el.textContent = "";
  }

  function badge(kind, value) {
    return "<span class=\"badge " + escapeHtml(kind) + "\">" + escapeHtml(value || "") + "</span>";
  }

  function formatDate(value) {
    if (!value) {
      return "—";
    }
    var date = new Date(value);
    if (isNaN(date.getTime())) {
      return String(value);
    }
    return date.toISOString().replace("T", " ").replace(/\.\d+Z$/, " UTC");
  }

  function fmtNum(value) {
    if (value == null || value === "") {
      return "—";
    }
    if (typeof value === "number" && !Number.isInteger(value)) {
      return value.toFixed(2);
    }
    return String(value);
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
})();
