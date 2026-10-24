import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ClienteService } from './cliente.service';

describe('ClienteService', () => {
  let service: ClienteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClienteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia os filtros de localidade junto da paginação', () => {
    service
      .listar({
        busca: '',
        tipo: '',
        status: 'todos',
        cidade: 'São Paulo',
        uf: 'SP',
        page: 2,
        size: 25,
      })
      .subscribe();

    const request = http.expectOne('/api/v1/clientes?page=2&size=25&cidade=S%C3%A3o%20Paulo&uf=SP');
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('omite os filtros de localidade vazios', () => {
    service
      .listar({
        busca: '',
        tipo: '',
        status: 'todos',
        cidade: '',
        uf: '',
        page: 0,
        size: 10,
      })
      .subscribe();

    const request = http.expectOne('/api/v1/clientes?page=0&size=10');
    expect(request.request.params.has('cidade')).toBe(false);
    expect(request.request.params.has('uf')).toBe(false);
    request.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });
});
