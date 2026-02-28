// JobRunr Agent Web UI

const API_BASE = '/api';
let settings = {
    agentName: 'Assistant',
    model: 'gpt-4o',
    instructions: 'You are a helpful AI assistant powered by JobRunr Agent.',
    maxTurns: 10
};
let chatHistory = [];
let streamingEnabled = true;

// DOM Elements
const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');
const agentNameEl = document.getElementById('agentName');
const modelBadge = document.getElementById('modelBadge');
const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');

// Navigation
document.querySelectorAll('.nav-link[data-view]').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        const view = link.dataset.view;

        // Update nav
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        link.classList.add('active');

        // Update views
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        document.getElementById(view + 'View').classList.add('active');

        if (view === 'settings') {
            loadSettings();
            loadProviderStatus();
            loadSessions();
        }
    });
});

// Chat
sendBtn.addEventListener('click', sendMessage);
chatInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// Auto-resize textarea
chatInput.addEventListener('input', () => {
    chatInput.style.height = 'auto';
    chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
});

// Model select updates badge
modelSelect.addEventListener('change', () => {
    modelBadge.textContent = modelSelect.options[modelSelect.selectedIndex].text;
});

// Streaming toggle
document.getElementById('streamToggle').addEventListener('change', (e) => {
    streamingEnabled = e.target.checked;
});

async function sendMessage() {
    const text = chatInput.value.trim();
    if (!text) return;

    // Add user message to UI
    addMessage('user', text);
    chatInput.value = '';
    chatInput.style.height = 'auto';

    // Add to history
    chatHistory.push({ role: 'user', content: text });

    // Disable input
    sendBtn.disabled = true;
    chatInput.disabled = true;

    if (streamingEnabled) {
        await sendMessageStreaming();
    } else {
        await sendMessageNonStreaming();
    }

    sendBtn.disabled = false;
    chatInput.disabled = false;
    chatInput.focus();
}

async function sendMessageStreaming() {
    // Create assistant message element for streaming
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = '';
    msgDiv.appendChild(contentDiv);
    chatMessages.appendChild(msgDiv);

    let fullContent = '';

    try {
        const response = await fetch(`${API_BASE}/chat/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: chatHistory,
                model: modelSelect.value,
                maxTurns: settings.maxTurns,
                contextVariables: {}
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value, { stream: true });
            // Parse SSE events
            const lines = chunk.split('\n');
            for (const line of lines) {
                if (line.startsWith('data:')) {
                    const data = line.substring(5).trim();
                    if (data) {
                        // Remove surrounding quotes if present (JSON string)
                        let token = data;
                        if (token.startsWith('"') && token.endsWith('"')) {
                            try { token = JSON.parse(token); } catch(e) { /* use raw */ }
                        }
                        fullContent += token;
                        contentDiv.textContent = fullContent;
                        chatMessages.scrollTop = chatMessages.scrollHeight;
                    }
                }
            }
        }

        chatHistory.push({ role: 'assistant', content: fullContent });

    } catch (error) {
        if (!fullContent) {
            msgDiv.remove();
        }
        addMessage('system', 'Error: ' + error.message);
    }
}

async function sendMessageNonStreaming() {
    const typingEl = showTyping();

    try {
        const response = await fetch(`${API_BASE}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: chatHistory,
                model: modelSelect.value,
                maxTurns: settings.maxTurns,
                contextVariables: {}
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        typingEl.remove();

        addMessage('assistant', data.response);
        chatHistory.push({ role: 'assistant', content: data.response });

        if (data.agent) {
            agentNameEl.textContent = data.agent;
        }

    } catch (error) {
        typingEl.remove();
        addMessage('system', 'Error: ' + error.message);
    }
}

function addMessage(role, content) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${role}`;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = content;

    msgDiv.appendChild(contentDiv);
    chatMessages.appendChild(msgDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function showTyping() {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    msgDiv.innerHTML = `
        <div class="typing-indicator">
            <span></span><span></span><span></span>
        </div>
    `;
    chatMessages.appendChild(msgDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return msgDiv;
}

// Settings
function loadSettings() {
    document.getElementById('settingAgentName').value = settings.agentName;
    document.getElementById('settingModel').value = settings.model;
    document.getElementById('settingInstructions').value = settings.instructions;
    document.getElementById('settingMaxTurns').value = settings.maxTurns;
}

document.getElementById('saveSettingsBtn').addEventListener('click', async () => {
    settings.agentName = document.getElementById('settingAgentName').value;
    settings.model = document.getElementById('settingModel').value;
    settings.instructions = document.getElementById('settingInstructions').value;
    settings.maxTurns = parseInt(document.getElementById('settingMaxTurns').value);

    // Update agent config on server
    try {
        const response = await fetch(`${API_BASE}/settings`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(settings)
        });

        if (response.ok) {
            agentNameEl.textContent = settings.agentName;
            modelBadge.textContent = settings.model;
            showToast('Settings saved', 'success');
        } else {
            showToast('Failed to save settings', 'error');
        }
    } catch (error) {
        // Settings saved locally even if server is down
        agentNameEl.textContent = settings.agentName;
        showToast('Settings saved locally', 'success');
    }
});

document.getElementById('clearChatBtn').addEventListener('click', () => {
    chatHistory = [];
    chatMessages.innerHTML = `
        <div class="message system">
            <div class="message-content">Chat history cleared. Start a new conversation.</div>
        </div>
    `;
    showToast('Chat cleared', 'success');
});

// Provider Status
async function loadProviderStatus() {
    try {
        const response = await fetch(`${API_BASE}/providers`);
        if (response.ok) {
            const providers = await response.json();
            updateProviderCard('providerOpenai', providers.openai);
            updateProviderCard('providerOllama', providers.ollama);
            updateProviderCard('providerAnthropic', providers.anthropic);
        }
    } catch (error) {
        // Silently fail
    }
}

function updateProviderCard(id, status) {
    const card = document.getElementById(id);
    if (!card) return;
    const icon = card.querySelector('.provider-icon');
    const statusEl = card.querySelector('.provider-status');

    if (status && status.available) {
        icon.textContent = '🟢';
        statusEl.textContent = status.model || 'Connected';
    } else {
        icon.textContent = '⚪';
        statusEl.textContent = 'Not configured';
    }
}

// Sessions
async function loadSessions() {
    try {
        const response = await fetch(`${API_BASE}/sessions`);
        if (response.ok) {
            const sessions = await response.json();
            const list = document.getElementById('sessionList');
            if (sessions.length === 0) {
                list.innerHTML = '<p class="muted">No sessions yet</p>';
                return;
            }
            list.innerHTML = sessions.map(s => `
                <div class="session-item">
                    <span>${s}</span>
                </div>
            `).join('');
        }
    } catch (error) {
        // Silently fail
    }
}

// Health check
async function checkHealth() {
    try {
        const response = await fetch(`${API_BASE}/health`);
        if (response.ok) {
            statusDot.className = 'status-dot connected';
            statusText.textContent = 'Connected';
        } else {
            statusDot.className = 'status-dot error';
            statusText.textContent = 'Error';
        }
    } catch (error) {
        statusDot.className = 'status-dot error';
        statusText.textContent = 'Disconnected';
    }
}

// Toast
function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// Init
checkHealth();
setInterval(checkHealth, 30000);
chatInput.focus();
