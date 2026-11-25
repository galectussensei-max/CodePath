import axiosInstance, { unwrap } from './axiosInstance.js';

/** @returns {Promise<{steps:Array<{topic:string,problems:Array<{questionId:number,slug:string,solved:boolean}>}>}>} */
export async function getRoadmap() {
  return unwrap(await axiosInstance.get('/api/roadmap'));
}

/**
 * The problem list has no per-learner status, but the roadmap flags every problem as solved or not,
 * so the Problems page derives its Status filter from it.
 * @returns {Promise<Set<string>>} slugs of the problems the learner has solved
 */
export async function getSolvedSlugs() {
  const roadmap = await getRoadmap();
  const solved = new Set();
  for (const step of roadmap?.steps ?? []) {
    for (const problem of step.problems ?? []) {
      if (problem.solved) {
        solved.add(problem.slug);
      }
    }
  }
  return solved;
}
