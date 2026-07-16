import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Overlay reutilizable para mostrar cargas indeterminadas o con porcentaje de avance.
 */
@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './loading.component.html',
  styleUrls: ['./loading.component.scss']
})
export class LoadingComponent {
  @Input() visible = false;
  @Input() message = 'Cargando...';
  @Input() detail = '';
  @Input() progress: number | null = null;
  @Input() indeterminate = true;
}
