import '../../test-setup';

import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { GameComponent } from '../pages/game.component';

describe('GameComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GameComponent, HttpClientTestingModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({})) },
        },
      ],
    });
  });

  it('buildMyBoardMaps collects ship coordinates', () => {
    const fixture = TestBed.createComponent(GameComponent);
    const component = fixture.componentInstance;

    component.myBoardState = {
      boardId: 'b1',
      width: 10,
      height: 10,
      locked: false,
      shipPlacements: [
        { type: 'DESTROYER', startX: 1, startY: 2, orientation: 'HORIZONTAL', size: 3 },
        { type: 'SUB', startX: 0, startY: 0, orientation: 'VERTICAL', size: 2 },
      ],
    };

    component.buildMyBoardMaps();

    expect(component.myShipCoords.has('1,2')).toBeTrue();
    expect(component.myShipCoords.has('2,2')).toBeTrue();
    expect(component.myShipCoords.has('3,2')).toBeTrue();
    expect(component.myShipCoords.has('0,0')).toBeTrue();
    expect(component.myShipCoords.has('0,1')).toBeTrue();
  });

  it('cellStateForMine prefers shot results over ships', () => {
    const fixture = TestBed.createComponent(GameComponent);
    const component = fixture.componentInstance;

    component.myShipCoords.add('4,4');
    expect(component.cellStateForMine(4, 4)).toBe('ship');

    component.shotsOnMyBoard.set('4,4', 'hit');
    expect(component.cellStateForMine(4, 4)).toBe('hit');
  });

  it('cellStateForOpp reflects shots on opponent board', () => {
    const fixture = TestBed.createComponent(GameComponent);
    const component = fixture.componentInstance;

    component.myShotsOnOpponent.set('1,1', 'miss');
    component.myShotsOnOpponent.set('2,2', 'hit');
    component.myShotsOnOpponent.set('3,3', 'sunk');

    expect(component.cellStateForOpp(1, 1)).toBe('miss');
    expect(component.cellStateForOpp(2, 2)).toBe('hit');
    expect(component.cellStateForOpp(3, 3)).toBe('sunk');
  });

  it('canShoot is true only when running and your turn', () => {
    const fixture = TestBed.createComponent(GameComponent);
    const component = fixture.componentInstance;

    component.game = {
      gameCode: 'G1',
      status: 'RUNNING',
      yourBoardLocked: true,
      opponentBoardLocked: true,
      yourTurn: true,
      opponentName: null,
    };

    component.shotLoading = false;
    expect(component.canShoot()).toBeTrue();

    component.shotLoading = true;
    expect(component.canShoot()).toBeFalse();
  });

  it('cellLabel returns grid coordinates', () => {
    const fixture = TestBed.createComponent(GameComponent);
    const component = fixture.componentInstance;

    expect(component.cellLabel(0, 10)).toBe('0,0');
    expect(component.cellLabel(11, 10)).toBe('1,1');
  });
});
