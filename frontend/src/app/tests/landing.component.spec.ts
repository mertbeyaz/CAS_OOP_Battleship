import '../../test-setup';
import { TestBed } from '@angular/core/testing';
import { LandingComponent } from '../pages/landing.component';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { API_BASE_URL } from '../app.config';

describe('LandingComponent', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [LandingComponent, HttpClientTestingModule, RouterTestingModule],
    });

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('quickPlay sets error when username is empty', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    const component = fixture.componentInstance;

    component.usernameQuick = '   ';
    component.quickPlay();

    expect(component.errorQuick).toBeTruthy();
    expect(component.loadingQuick).toBeFalse();
  });

  it('quickPlay sends request and navigates on success', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    const component = fixture.componentInstance;

    component.usernameQuick = 'Ray';
    component.quickPlay();

    const req = httpMock.expectOne(`${API_BASE_URL}/lobbies/auto-join`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'Ray' });

    req.flush({
      lobbyCode: 'L1',
      gameCode: 'G1',
      status: 'WAITING',
      myPlayerId: 'P1',
      myPlayerName: 'Ray',
      myBoard: null,
      resumeToken: 'T1',
    });

    expect(component.loadingQuick).toBeFalse();
    expect(router.navigate).toHaveBeenCalled();

    const stored = localStorage.getItem('resumePlayer:T1');
    expect(stored).toContain('"gameCode":"G1"');
  });

  it('joinByCode sets error when resume token is empty', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    const component = fixture.componentInstance;

    component.resumeTokenJoin = '   ';
    component.joinByCode();

    expect(component.errorJoin).toBeTruthy();
    expect(component.loadingJoin).toBeFalse();
  });

  it('joinByCode sends resume request and navigates on success', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    const component = fixture.componentInstance;

    const token = 'T2';
    localStorage.setItem(
      `resumePlayer:${token}`,
      JSON.stringify({ gameCode: 'G2', playerId: 'P2', playerName: 'Max' })
    );

    component.resumeTokenJoin = token;
    component.joinByCode();

    const req = httpMock.expectOne(`${API_BASE_URL}/games/resume`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token });

    req.flush({
      status: 'WAITING',
    });

    expect(component.loadingJoin).toBeFalse();
    expect(router.navigate).toHaveBeenCalled();
  });
});
