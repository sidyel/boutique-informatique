import { TestBed } from '@angular/core/testing';

import { Squelette } from './squelette';

describe('Squelette', () => {
  it('est ignoré par les lecteurs d’écran et a des dimensions fixes', () => {
    const fixture = TestBed.createComponent(Squelette);
    fixture.componentRef.setInput('forme', 'bloc');
    fixture.componentRef.setInput('largeur', '300px');
    fixture.componentRef.setInput('hauteur', '200px');
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.getAttribute('aria-hidden')).toBe('true');
    expect(element.classList).toContain('squelette--bloc');
    expect(element.style.width).toBe('300px');
    expect(element.style.height).toBe('200px');
  });

  it('donne à un cercle une hauteur égale à sa largeur par défaut', () => {
    const fixture = TestBed.createComponent(Squelette);
    fixture.componentRef.setInput('forme', 'cercle');
    fixture.componentRef.setInput('largeur', '48px');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).style.height).toBe('48px');
  });
});
