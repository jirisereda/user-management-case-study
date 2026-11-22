import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface User {
  id: number;
  name: string;
  email: string;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/users';

  list(): Observable<User[]> {
    return this.http.get<User[]>(this.url);
  }

  create(name: string, email: string): Observable<User> {
    return this.http.post<User>(this.url, { name, email });
  }

  // Only the name is sent; the version enables optimistic locking on the backend.
  update(user: User, name: string): Observable<User> {
    return this.http.put<User>(`${this.url}/${user.id}`, { name, version: user.version });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
