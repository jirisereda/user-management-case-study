import { Component, signal } from '@angular/core';

@Component({
  imports: [],
  selector: 'app-root',
  styles: [],
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');
}
