import axiosInstance, { unwrap } from './axiosInstance.js';

/**
 * Diagnostic questions carry only a prompt and the A-D options; the answer key never leaves the server.
 * @returns {Promise<Array<{id:number,title:string,topic:string,difficulty:string,prompt:string,options:Array<{label:string,text:string}>}>>}
 */
export async function getDiagnostic() {
  return unwrap(await axiosInstance.get('/api/assessment/diagnostic'));
}

/**
 * Every diagnostic question must be answered exactly once (409 if the diagnostic was already completed).
 * @param {Array<{questionId:number, answer:string}>} answers
 * @returns {Promise<{totalQuestions:number, correctCount:number, note:string,
 *   topics:Array<{topicId:number,topicName:string,correct:number,total:number,masteryScore:number,status:string}>}>}
 */
export async function submitDiagnostic(answers) {
  return unwrap(await axiosInstance.post('/api/assessment/submit', { answers }));
}
