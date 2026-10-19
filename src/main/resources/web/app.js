const menuScreen = document.querySelector("#menu-screen");
const gameScreen = document.querySelector("#game-screen");
const board = document.querySelector("#board");
const menuError = document.querySelector("#menu-error");
const gameError = document.querySelector("#game-error");
const sessionKey = "othello-web-session";

let access = loadAccess();
let currentState = null;
let displayedBoard = null;
let pollTimer = null;
let isRequesting = false;
let animating = false;
let animationToken = 0;
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

async function request(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (access?.token) headers["X-Player-Token"] = access.token;
  const response = await fetch(path, { ...options, headers });
  let payload;
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }
  if (!response.ok) throw new Error(payload.error || "The server could not process the request.");
  return payload;
}

async function createGame(settings) {
  setMenuError("");
  try {
    const created = await request("/api/games", {
      method: "POST",
      body: JSON.stringify(settings)
    });
    useAccess(created);
  } catch (error) {
    setMenuError(error.message);
  }
}

async function useAccess(next) {
  access = { gameId: next.gameId, token: next.token };
  localStorage.setItem(sessionKey, JSON.stringify(access));
  displayedBoard = null;
  board.replaceChildren();
  menuScreen.classList.add("hidden");
  gameScreen.classList.remove("hidden");
  await present(next.state, next.frames);
  restartPolling();
}

function showMenu() {
  clearInterval(pollTimer);
  pollTimer = null;
  animationToken++;
  access = null;
  currentState = null;
  displayedBoard = null;
  animating = false;
  board.replaceChildren();
  localStorage.removeItem(sessionKey);
  gameScreen.classList.add("hidden");
  menuScreen.classList.remove("hidden");
  history.replaceState({}, "", location.pathname);
}

function ensureBoard() {
  if (board.childElementCount === 64) return;
  board.replaceChildren();
  for (let index = 0; index < 64; index++) {
    const row = Math.floor(index / 8);
    const col = index % 8;
    const square = document.createElement("button");
    square.type = "button";
    square.className = `square ${(row + col) % 2 === 0 ? "light" : "dark"}`;
    square.setAttribute("role", "gridcell");
    square.setAttribute("aria-label", `${String.fromCharCode(97 + col)}${row + 1}`);
    square.addEventListener("click", () => play(row, col));
    board.append(square);
  }
}

function renderChrome(state) {
  document.querySelector("#game-code").textContent =
    state.mode === "lan" ? state.gameId : state.mode;
  document.querySelector("#black-name").textContent = state.blackName;
  document.querySelector("#white-name").textContent = state.whiteName;
  document.querySelector("#black-count").textContent = state.blackCount;
  document.querySelector("#white-count").textContent = state.whiteCount;
  document.querySelector("#status").textContent = state.status;
  document.querySelector("#player-side").textContent =
    state.yourColor === "BOTH" ? "Shared board" : `You are ${title(state.yourColor)}`;
  document.querySelector("#black-score").classList.toggle(
    "active", !state.finished && !animating && state.turn === "BLACK");
  document.querySelector("#white-score").classList.toggle(
    "active", !state.finished && !animating && state.turn === "WHITE");
  const undo = document.querySelector("#undo-button");
  undo.classList.toggle("hidden", state.mode === "lan");
  undo.disabled = !state.canUndo || isRequesting || animating;
  const resign = document.querySelector("#resign-button");
  resign.classList.toggle("hidden", state.mode === "hotseat");
  resign.disabled = state.finished || state.waiting || isRequesting || animating;
  document.querySelector("#share-button").classList.toggle("hidden", state.mode !== "lan");
}

function paintInstant(boardText, state) {
  ensureBoard();
  [...board.children].forEach((square, index) => {
    const symbol = boardText[index];
    square.querySelector(".hint")?.remove();
    square.querySelector(".mark")?.remove();
    const existing = square.querySelector(".disc");
    if (symbol === ".") {
      existing?.remove();
      return;
    }
    const disc = existing || document.createElement("span");
    disc.className = `disc ${symbol === "B" ? "black" : "white"}`;
    if (!existing) square.append(disc);
  });
  if (state) markLast(state);
}

function syncInteractions(state) {
  const legal = new Set((state?.legalMoves || []).map(move => `${move.row},${move.col}`));
  [...board.children].forEach((square, index) => {
    const row = Math.floor(index / 8);
    const col = index % 8;
    const isLegal = !animating && !isRequesting && legal.has(`${row},${col}`);
    square.classList.toggle("legal", isLegal);
    square.disabled = !isLegal;
    square.querySelector(".hint")?.remove();
    if (isLegal) {
      const hint = document.createElement("span");
      hint.className = "hint";
      square.append(hint);
    }
  });
}

async function present(state, frames) {
  const token = ++animationToken;
  const steps = Array.isArray(frames) && frames.length ? frames : [state];
  ensureBoard();
  if (displayedBoard && displayedBoard === state.board
      && steps.every(step => step.board === displayedBoard)) {
    currentState = state;
    animating = false;
    renderChrome(state);
    syncInteractions(state);
    return;
  }
  if (!displayedBoard) {
    const opening = initialBoard();
    const shouldAnimate = !reduceMotion && steps.some(step => step.board !== opening);
    paintInstant(shouldAnimate ? opening : state.board, shouldAnimate ? null : state);
    displayedBoard = shouldAnimate ? opening : state.board;
    if (!shouldAnimate) {
      currentState = state;
      renderChrome(state);
      syncInteractions(state);
      return;
    }
    animating = true;
    document.querySelector("#status").textContent = "Computer is thinking…";
    await wait(280);
    if (token !== animationToken) return;
  }
  animating = true;
  renderChrome(state);
  syncInteractions(state);
  let previous = displayedBoard;
  try {
    for (let index = 0; index < steps.length; index++) {
      if (token !== animationToken) return;
      const frame = steps[index];
      if (!frame?.board || frame.board === previous) continue;
      const nextCount = [...frame.board].filter(symbol => symbol !== ".").length;
      const previousCount = [...previous].filter(symbol => symbol !== ".").length;
      if (index > 0 && nextCount > previousCount) {
        document.querySelector("#status").textContent = "Computer is thinking…";
        await wait(reduceMotion ? 0 : 420);
        if (token !== animationToken) return;
      }
      await animateStep(previous, frame);
      previous = frame.board;
      displayedBoard = previous;
      document.querySelector("#black-count").textContent = frame.blackCount;
      document.querySelector("#white-count").textContent = frame.whiteCount;
    }
  } finally {
    if (token === animationToken) {
      animating = false;
      currentState = state;
      displayedBoard = state.board;
      paintInstant(state.board, state);
      renderChrome(state);
      syncInteractions(state);
    }
  }
}

async function animateStep(before, frame) {
  if (reduceMotion || before === frame.board) {
    paintInstant(frame.board, frame);
    return;
  }
  board.querySelectorAll(".hint, .mark").forEach(node => node.remove());
  const tasks = [];
  for (let index = 0; index < 64; index++) {
    if (before[index] === frame.board[index]) continue;
    const row = Math.floor(index / 8);
    const col = index % 8;
    const removed = before[index] !== "." && frame.board[index] === ".";
    const distance = removed || frame.lastRow < 0
      ? 0
      : Math.abs(row - frame.lastRow) + Math.abs(col - frame.lastCol);
    tasks.push(animateCell(board.children[index], before[index], frame.board[index], distance));
  }
  await Promise.all(tasks);
  markLast(frame);
}

async function animateCell(square, from, to, distance) {
  await wait(distance * 46);
  if (from === "." && to !== ".") {
    const disc = document.createElement("span");
    disc.className = `disc ${to === "B" ? "black" : "white"} placing`;
    square.append(disc);
    await animationDone(disc, 380);
    disc.classList.remove("placing");
    return;
  }
  const disc = square.querySelector(".disc");
  if (!disc) return;
  if (to === ".") {
    disc.classList.add("removing");
    await animationDone(disc, 260);
    disc.remove();
    return;
  }
  disc.classList.add("flip-out");
  await wait(190);
  disc.classList.remove("black", "white", "flip-out");
  disc.classList.add(to === "B" ? "black" : "white", "flip-in");
  await animationDone(disc, 260);
  disc.classList.remove("flip-in");
}

function markLast(state) {
  board.querySelectorAll(".mark").forEach(node => node.remove());
  if (!state || state.lastRow < 0 || state.lastCol < 0) return;
  const mark = document.createElement("span");
  mark.className = "mark";
  board.children[state.lastRow * 8 + state.lastCol]?.append(mark);
}

function initialBoard() {
  const cells = Array(64).fill(".");
  cells[3 * 8 + 3] = "W";
  cells[3 * 8 + 4] = "B";
  cells[4 * 8 + 3] = "B";
  cells[4 * 8 + 4] = "W";
  return cells.join("");
}

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function animationDone(element, fallbackMs) {
  return new Promise(resolve => {
    const finish = () => {
      element.removeEventListener("animationend", finish);
      resolve();
    };
    element.addEventListener("animationend", finish);
    setTimeout(resolve, fallbackMs);
  });
}

async function play(row, col) {
  if (isRequesting || animating) return;
  isRequesting = true;
  setGameError("");
  syncInteractions(currentState);
  try {
    const result = await request(`/api/games/${access.gameId}/move`, {
      method: "POST",
      body: JSON.stringify({ row, col })
    });
    await present(result.state, result.frames);
  } catch (error) {
    setGameError(error.message);
    syncInteractions(currentState);
  } finally {
    isRequesting = false;
    if (currentState) syncInteractions(currentState);
  }
}

async function gameAction(action) {
  if (isRequesting || animating) return;
  isRequesting = true;
  setGameError("");
  syncInteractions(currentState);
  try {
    const result = await request(`/api/games/${access.gameId}/${action}`, { method: "POST" });
    await present(result.state || result, result.frames);
  } catch (error) {
    setGameError(error.message);
  } finally {
    isRequesting = false;
    if (currentState) syncInteractions(currentState);
  }
}

function restartPolling() {
  clearInterval(pollTimer);
  if (currentState?.mode !== "lan") return;
  pollTimer = setInterval(refreshState, 900);
}

async function refreshState() {
  if (!access || isRequesting || animating) return;
  try {
    const state = await request(`/api/games/${access.gameId}`);
    await present(state);
  } catch (error) {
    setGameError(error.message);
  }
}

document.querySelector("#computer-form").addEventListener("submit", event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  createGame({
    mode: "computer",
    name: form.get("name"),
    color: form.get("color"),
    difficulty: form.get("difficulty")
  });
});

document.querySelector("#hotseat-button").addEventListener("click", () =>
  createGame({ mode: "hotseat", name: "Players", color: "BLACK", difficulty: "EASY" }));

document.querySelector("#host-form").addEventListener("submit", event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  createGame({
    mode: "lan",
    name: form.get("name"),
    color: form.get("color"),
    difficulty: "EASY"
  });
});

document.querySelector("#join-form").addEventListener("submit", async event => {
  event.preventDefault();
  setMenuError("");
  const form = new FormData(event.currentTarget);
  const code = String(form.get("code")).trim().toUpperCase();
  try {
    const joined = await request(`/api/games/${encodeURIComponent(code)}/join`, {
      method: "POST",
      body: JSON.stringify({ name: form.get("name") })
    });
    useAccess(joined);
  } catch (error) {
    setMenuError(error.message);
  }
});

document.querySelector("#undo-button").addEventListener("click", () => gameAction("undo"));
document.querySelector("#resign-button").addEventListener("click", () => {
  if (confirm("Resign this game?")) gameAction("resign");
});
document.querySelector("#back-button").addEventListener("click", showMenu);
document.querySelector("#share-button").addEventListener("click", async event => {
  const link = `${location.origin}${location.pathname}?join=${currentState.gameId}`;
  try {
    await navigator.clipboard.writeText(link);
    event.currentTarget.textContent = "Link copied";
    setTimeout(() => { event.currentTarget.textContent = "Share game"; }, 1600);
  } catch {
    prompt("Send this link to the other player:", link);
  }
});

const rules = document.querySelector("#rules-dialog");
document.querySelector("#rules-button").addEventListener("click", () => rules.showModal());
document.querySelector("#close-rules").addEventListener("click", () => rules.close());

function setMenuError(message) { menuError.textContent = message; }
function setGameError(message) { gameError.textContent = message; }
function title(value) {
  const text = String(value || "").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
function loadAccess() {
  try {
    const saved = JSON.parse(localStorage.getItem(sessionKey));
    return saved?.gameId && saved?.token ? saved : null;
  } catch {
    return null;
  }
}

const joinCode = new URLSearchParams(location.search).get("join");
if (joinCode) document.querySelector("#join-code").value = joinCode.toUpperCase();
if (access) {
  refreshState().then(() => {
    if (currentState) {
      menuScreen.classList.add("hidden");
      gameScreen.classList.remove("hidden");
      restartPolling();
    }
  });
}
