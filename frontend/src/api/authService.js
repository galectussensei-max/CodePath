import axiosInstance, { unwrap } from './axiosInstance.js';

/** @returns {Promise<{accessToken:string, tokenType:string, expiresInMs:number, user:{id:number,name:string,email:string,role:string,diagnosticCompleted:boolean}}>} */
export async function login(email, password) {
  const response = await axiosInstance.post('/api/auth/login', { email, password });
  return unwrap(response);
}

export async function register(name, email, password) {
  const response = await axiosInstance.post('/api/auth/register', { name, email, password });
  return unwrap(response);
}
