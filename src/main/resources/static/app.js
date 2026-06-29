const state = {
  history: [],
  images: [],
  busy: false
};

const els = {
  statusText: document.querySelector("#statusText"),
  messages: document.querySelector("#messages"),
  messageInput: document.querySelector("#messageInput"),
  sendButton: document.querySelector("#sendButton"),
  clearButton: document.querySelector("#clearButton"),
  ragToggle: document.querySelector("#ragToggle"),
  imageInput: document.querySelector("#imageInput"),
  imagePreview: document.querySelector("#imagePreview"),
  docInput: document.querySelector("#docInput"),
  dropZone: document.querySelector("#dropZone"),
  documents: document.querySelector("#documents"),
  refreshDocs: document.querySelector("#refreshDocs"),
  searchInput: document.querySelector("#searchInput"),
  searchButton: document.querySelector("#searchButton"),
  searchResults: document.querySelector("#searchResults")
};

bootstrap();

function bootstrap() {
  addMessage("assistant", "你好，我已经准备好。你可以先上传知识库文档，再打开 RAG 提问；也可以附加图片进行多模态对话。");
  bindEvents();
  loadStatus();
  loadDocuments();
}

function bindEvents() {
  els.sendButton.addEventListener("click", sendMessage);
  els.clearButton.addEventListener("click", clearConversation);
  els.messageInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      sendMessage();
    }
  });
  els.imageInput.addEventListener("change", async () => {
    await addImages(Array.from(els.imageInput.files));
    els.imageInput.value = "";
  });
  els.docInput.addEventListener("change", async () => {
    await uploadDocuments(Array.from(els.docInput.files));
    els.docInput.value = "";
  });
  els.refreshDocs.addEventListener("click", loadDocuments);
  els.searchButton.addEventListener("click", runSearch);
  els.searchInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      runSearch();
    }
  });

  ["dragenter", "dragover"].forEach((name) => {
    els.dropZone.addEventListener(name, (event) => {
      event.preventDefault();
      els.dropZone.classList.add("dragging");
    });
  });
  ["dragleave", "drop"].forEach((name) => {
    els.dropZone.addEventListener(name, (event) => {
      event.preventDefault();
      els.dropZone.classList.remove("dragging");
    });
  });
  els.dropZone.addEventListener("drop", async (event) => {
    await uploadDocuments(Array.from(event.dataTransfer.files));
  });
}

async function loadStatus() {
  try {
    const status = await apiJson("/api/status");
    els.statusText.textContent = status.aiConfigured
      ? `已连接模型：${status.chatModel} / ${status.embeddingModel}`
      : "本地演示模式：配置 APP_AI_API_KEY 后启用真实模型";
  } catch (error) {
    els.statusText.textContent = `服务状态异常：${error.message}`;
  }
}

async function loadDocuments() {
  try {
    const docs = await apiJson("/api/knowledge/documents");
    renderDocuments(docs);
  } catch (error) {
    els.documents.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
  }
}

async function sendMessage() {
  const message = els.messageInput.value.trim();
  if (!message && state.images.length === 0) {
    return;
  }
  if (state.busy) {
    return;
  }

  setBusy(true);
  const images = [...state.images];
  els.messageInput.value = "";
  state.images = [];
  renderImagePreview();

  addMessage("user", message || "请分析我上传的图片。", [], images);
  const pending = addMessage("assistant", "正在思考...");

  try {
    const response = await apiJson("/api/chat", {
      method: "POST",
      body: JSON.stringify({
        message,
        history: state.history.slice(-10),
        images,
        useRag: els.ragToggle.checked,
        topK: 4
      })
    });
    pending.remove();
    addMessage("assistant", response.answer, response.citations || []);
    state.history.push({ role: "user", content: message });
    state.history.push({ role: "assistant", content: response.answer });
  } catch (error) {
    pending.remove();
    addMessage("assistant", `请求失败：${error.message}`);
  } finally {
    setBusy(false);
  }
}

function clearConversation() {
  state.history = [];
  state.images = [];
  els.messages.innerHTML = "";
  renderImagePreview();
  addMessage("assistant", "对话已清空。知识库仍然保留，可以继续提问。");
}

async function addImages(files) {
  const imageFiles = files.filter((file) => file.type.startsWith("image/"));
  for (const file of imageFiles) {
    const dataUrl = await readAsDataUrl(file);
    state.images.push({
      filename: file.name,
      contentType: file.type,
      dataUrl
    });
  }
  renderImagePreview();
}

function renderImagePreview() {
  els.imagePreview.innerHTML = "";
  for (const image of state.images) {
    const chip = document.createElement("div");
    chip.className = "image-chip";
    chip.innerHTML = `
      <img src="${image.dataUrl}" alt="">
      <span title="${escapeHtml(image.filename)}">${escapeHtml(image.filename)}</span>
    `;
    els.imagePreview.appendChild(chip);
  }
}

async function uploadDocuments(files) {
  const docs = files.filter(Boolean);
  if (docs.length === 0) {
    return;
  }
  els.documents.innerHTML = `<p class="empty">正在上传并构建索引...</p>`;
  try {
    for (const file of docs) {
      const form = new FormData();
      form.append("file", file);
      await apiJson("/api/knowledge/documents", {
        method: "POST",
        body: form,
        headers: {}
      });
    }
    await loadDocuments();
  } catch (error) {
    els.documents.innerHTML = `<p class="empty">上传失败：${escapeHtml(error.message)}</p>`;
  }
}

function renderDocuments(docs) {
  if (!docs.length) {
    els.documents.innerHTML = `<p class="empty">还没有文档。可以上传示例或你的业务资料开始构建知识库。</p>`;
    return;
  }
  els.documents.innerHTML = "";
  for (const doc of docs) {
    const item = document.createElement("article");
    item.className = "doc-item";
    item.innerHTML = `
      <div class="doc-title">
        <span>${escapeHtml(doc.filename)}</span>
        <button class="delete-doc" type="button" title="删除">×</button>
      </div>
      <p class="meta">${doc.chunks} 个片段 · ${formatBytes(doc.sizeBytes)} · ${new Date(doc.uploadedAt).toLocaleString()}</p>
    `;
    item.querySelector("button").addEventListener("click", async () => {
      await apiJson(`/api/knowledge/documents/${doc.id}`, { method: "DELETE" });
      await loadDocuments();
    });
    els.documents.appendChild(item);
  }
}

async function runSearch() {
  const query = els.searchInput.value.trim();
  if (!query) {
    return;
  }
  els.searchResults.innerHTML = `<p class="empty">检索中...</p>`;
  try {
    const results = await apiJson(`/api/knowledge/search?q=${encodeURIComponent(query)}&topK=5`);
    if (!results.length) {
      els.searchResults.innerHTML = `<p class="empty">没有找到相关片段。</p>`;
      return;
    }
    els.searchResults.innerHTML = "";
    for (const result of results) {
      const item = document.createElement("article");
      item.className = "search-item";
      item.innerHTML = `
        <p><strong>${escapeHtml(result.filename)}</strong> <span class="meta">#${result.chunkIndex} · ${Number(result.score).toFixed(2)}</span></p>
        <p>${escapeHtml(clip(result.preview, 220))}</p>
      `;
      els.searchResults.appendChild(item);
    }
  } catch (error) {
    els.searchResults.innerHTML = `<p class="empty">${escapeHtml(error.message)}</p>`;
  }
}

function addMessage(role, content, citations = [], images = []) {
  const wrapper = document.createElement("article");
  wrapper.className = `message ${role}`;
  const imageHtml = images.map((image) => `
    <div class="image-chip">
      <img src="${image.dataUrl}" alt="">
      <span>${escapeHtml(image.filename)}</span>
    </div>
  `).join("");
  const citationHtml = citations.length ? `
    <div class="citations">
      ${citations.map((citation, index) => `
        <div class="citation">
          <strong>[${index + 1}] ${escapeHtml(citation.filename)} #${citation.chunkIndex} · ${Number(citation.score).toFixed(2)}</strong>
          ${escapeHtml(clip(citation.preview, 260))}
        </div>
      `).join("")}
    </div>
  ` : "";
  wrapper.innerHTML = `
    ${imageHtml ? `<div class="image-preview">${imageHtml}</div>` : ""}
    <div class="bubble">${escapeHtml(content || "")}</div>
    ${citationHtml}
  `;
  els.messages.appendChild(wrapper);
  els.messages.scrollTop = els.messages.scrollHeight;
  return wrapper;
}

async function apiJson(url, options = {}) {
  const headers = options.body instanceof FormData
    ? options.headers
    : { "Content-Type": "application/json", ...(options.headers || {}) };
  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch (_) {
      message = await response.text();
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function setBusy(isBusy) {
  state.busy = isBusy;
  els.sendButton.disabled = isBusy;
  els.messageInput.disabled = isBusy;
}

function clip(text, maxLength) {
  if (!text || text.length <= maxLength) {
    return text || "";
  }
  return `${text.slice(0, maxLength)}...`;
}

function formatBytes(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
