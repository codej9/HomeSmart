// Run on the evidence instance after `hs-evidence start` with the door locked:
//   HS_EVIDENCE_PASSWORD=... k6 run src/test/k6/workload.js
// After the run, TURN_ON and TURN_OFF rows for the light rise by exactly the `toggles` count.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8083/homesmart';
const USER = 'evidence';
const PASSWORD = __ENV.HS_EVIDENCE_PASSWORD;
const LIGHT_ID = '1';
const toggles = new Counter('toggles');
const LOGIN = { tags: { phase: 'login' } };
const WORK = { tags: { phase: 'work' } };

if (!PASSWORD) {
  throw new Error('Set HS_EVIDENCE_PASSWORD');
}

export const options = {
  vus: 20,
  duration: '60s',
  thresholds: {
    'http_req_failed{phase:work}': ['rate<0.01'],
    'http_req_duration{phase:work}': ['p(95)<500'],
    'http_reqs{phase:work}': ['count>100'],
    checks: ['rate>0.99'],
  },
};

// Each VU runs its own module instance, so `token` is per VU.
let token = null;

function csrfOf(res) {
  return res.html('input[name=_csrf]').attr('value');
}

function signIn() {
  const page = http.get(`${BASE}/login`, LOGIN);
  const res = http.post(`${BASE}/login`,
    { username: USER, password: PASSWORD, _csrf: csrfOf(page) }, LOGIN);
  check(res, { 'signed in': (r) => r.status === 200 && !r.url.endsWith('/login') });
  token = csrfOf(res);
}

function expectPage(res, marker) {
  token = csrfOf(res) || token;
  return check(res, {
    'status is 200': (r) => r.status === 200,
    [`shows ${marker}`]: (r) => r.body.includes(marker),
  });
}

function devices() {
  expectPage(http.get(`${BASE}/devices`, WORK), 'Current setpoint');
}

function toggleLight() {
  const res = http.post(`${BASE}/devices`,
    { deviceId: LIGHT_ID, action: 'toggle', _csrf: token }, WORK);
  if (expectPage(res, 'Current setpoint')) {
    toggles.add(1);
  }
}

function activity() {
  expectPage(http.get(`${BASE}/activity`, WORK), 'Activity log');
}

function energy() {
  expectPage(http.get(`${BASE}/panels/energy`, WORK), 'Estimated energy use');
}

export default function () {
  if (!token) {
    signIn();
  }
  const roll = Math.random();
  if (roll < 0.6) {
    devices();
  } else if (roll < 0.85) {
    toggleLight();
  } else if (roll < 0.95) {
    activity();
  } else {
    energy();
  }
  sleep(1);
}
