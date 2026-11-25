import axiosInstance, { unwrap } from './axiosInstance.js';

/**
 * The whole practice problem bank (diagnostic questions are never included).
 * @returns {Promise<Array<{id:number,slug:string,title:string,topicId:number,topic:string,conceptId:number,
 *   concept:string,difficulty:'EASY'|'MEDIUM'|'HARD',placementTags:string[]}>>}
 */
export async function listQuestions() {
  return unwrap(await axiosInstance.get('/api/questions'));
}

/**
 * One problem with its PUBLIC sample cases only (hidden cases never leave the server).
 * Rejects with a 404 for an unknown slug.
 */
export async function getQuestionBySlug(slug) {
  return unwrap(await axiosInstance.get(`/api/questions/${encodeURIComponent(slug)}`));
}
