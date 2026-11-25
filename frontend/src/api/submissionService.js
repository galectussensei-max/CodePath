import axiosInstance, { unwrap } from './axiosInstance.js';

/**
 * Verified Judge0 run against the PUBLIC sample cases. Nothing is saved and mastery is unchanged.
 * @returns {Promise<SubmissionResult>}
 */
export async function runCode({ questionId, sourceCode }) {
  return unwrap(await axiosInstance.post('/api/submissions/run', { questionId, sourceCode }));
}

/**
 * Verified Judge0 run against public AND hidden cases. Saves the submission and updates the mastery estimate.
 * @returns {Promise<SubmissionResult>}
 */
export async function submitCode({ questionId, sourceCode, timeTakenSeconds }) {
  return unwrap(await axiosInstance.post('/api/submissions/submit', { questionId, sourceCode, timeTakenSeconds }));
}

/**
 * The learner's own past submissions for a problem, newest first.
 * @returns {Promise<Array<{id:number,verdict:string,executionTimeMs:number|null,memoryUsedKb:number|null,
 *   hintsUsedCount:number,sourceCode:string,createdAt:string}>>}
 */
export async function getSubmissionHistory(questionId) {
  return unwrap(await axiosInstance.get(`/api/submissions/question/${questionId}`));
}

/**
 * @typedef {Object} SubmissionResult
 * @property {'RUN'|'SUBMIT'} mode
 * @property {number} [submissionId]
 * @property {string} verdict
 * @property {number} [executionTimeMs]
 * @property {number} [memoryUsedKb]
 * @property {number} passedCount
 * @property {number} totalCount
 * @property {string} [compileOutput]
 * @property {Array<Object>} testCases  hidden cases carry only {hidden, verdict, passed, timeMs, memoryKb}
 * @property {Object} [masteryUpdate]
 */
