import { Routes } from '@angular/router';
import { LandingComponent } from './pages/landing.component';
import { GameComponent } from './pages/game.component';

export const routes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'game', component: GameComponent },
  { path: '**', redirectTo: '' }
];
