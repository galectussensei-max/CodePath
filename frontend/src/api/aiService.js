import axiosInstance, { unwrap } from './axiosInstance.js';

/**
 * Staged AI hint (levels 1-4). Level N needs level N-1 first (409 otherwise). Everything returned is
 * UNVERIFIED guidance: `isVerified` is always false and only the judge's verdict is authoritative.
 * @returns {Promise<AiGuidance>}
 */
export async function requestHint({ questionId, sourceCode, verdict, hintLevel }) {
  const body = { questionId, hintLevel };
  if (sourceCode) {
    body.sourceCode = sourceCode;
  }
  if (verdict) {
    body.verdict = verdict;
  }
  return unwrap(await axiosInstance.post('/api/ai/hint', body));
}

/**
 * Complexity review of the learner's saved ACCEPTED submission (code is read server-side, never sent).
 * @returns {Promise<AiGuidance>}
 */
export async function requestReview({ questionId, submissionId }) {
  const body = { questionId };
  if (submissionId) {
    body.submissionId = submissionId;
  }
  return unwrap(await axiosInstance.post('/api/ai/review', body));
}

/**
 * @typedef {Object} AiGuidance
 * @property {number} interactionId
 * @property {'HINT_L1'|'HINT_L2'|'HINT_L3'|'HINT_L4'|'AC_REVIEW'} interactionType
 * @property {number|null} hintLevel
 * @property {string} content
 * @property {boolean} isVerified  always false
 * @property {string} label
 * @property {number} hintsUsedForQuestion
 * @property {string} createdAt
 */
