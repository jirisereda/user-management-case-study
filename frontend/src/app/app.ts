import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { User, UserService } from './user.service';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styles: `
    :host { display: block; max-width: 720px; margin: 2rem auto; font-family: sans-serif; }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th, td { text-align: left; padding: 0.4rem; border-bottom: 1px solid #ddd; }
    form { display: flex; gap: 0.5rem; align-items: flex-start; flex-wrap: wrap; }
    label { display: flex; flex-direction: column; gap: 0.2rem; }
    .hint { color: #b00020; font-size: 0.8rem; }
    .error { color: #b00020; background: #fdecea; padding: 0.5rem; margin-top: 0.5rem; }
    input:disabled { background: #eee; }
  `,
})
export class App implements OnInit {
  private readonly userService = inject(UserService);

  protected readonly users = signal<User[]>([]);
  protected readonly editing = signal<User | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  ngOnInit(): void {
    this.load();
  }

  protected edit(user: User): void {
    this.error.set(null);
    this.editing.set(user);
    this.form.setValue({ name: user.name, email: user.email });
    this.form.controls.email.disable(); // email is read-only after creation
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset();
    this.form.controls.email.enable();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    const { name, email } = this.form.getRawValue();
    const user = this.editing();
    const request: Observable<User> = user
      ? this.userService.update(user, name)
      : this.userService.create(name, email);

    request.subscribe({
      next: () => {
        this.cancel();
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.showError(err);
        // The edited user was changed or deleted meanwhile: drop the stale edit and refresh.
        if (user && (err.status === 409 || err.status === 404)) {
          this.cancel();
          this.load();
        }
      },
    });
  }

  protected remove(user: User): void {
    if (!confirm(`Delete user ${user.name}?`)) {
      return;
    }
    this.error.set(null);
    this.userService.delete(user.id).subscribe({
      next: () => {
        if (this.editing()?.id === user.id) {
          this.cancel();
        }
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.showError(err);
        this.load();
      },
    });
  }

  private load(): void {
    this.userService.list().subscribe({
      next: (users) => this.users.set(users),
      error: (err: HttpErrorResponse) => this.showError(err),
    });
  }

  // Backend errors are RFC 9457 problem details, optionally with per-field validation errors.
  private showError(err: HttpErrorResponse): void {
    const problem = err.error;
    if (problem?.errors) {
      const fields = Object.entries(problem.errors as Record<string, string>)
        .map(([field, message]) => `${field}: ${message}`)
        .join(', ');
      this.error.set(`${problem.detail}: ${fields}`);
    } else {
      this.error.set(problem?.detail ?? `Request failed (${err.status || 'server unreachable'})`);
    }
  }
}
