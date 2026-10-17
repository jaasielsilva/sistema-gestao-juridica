"""
Gerador do Relatorio de Auditoria de Seguranca — GENERICO
Uso: python gerar_relatorio.py
Saida: relatorio-auditoria-seguranca.pdf (mesmo diretorio)
Dependencias: reportlab, matplotlib
"""

import json
import os
import io
from datetime import datetime
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    Table, TableStyle, Image, HRFlowable, PageBreak, KeepTogether,
    NextPageTemplate
)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.pdfgen import canvas as pdfcanvas

# ── Paleta ───────────────────────────────────────────────────────────────
COR = {
    "critica":    "#B91C1C",
    "alta":       "#EA580C",
    "media":      "#D97706",
    "baixa":      "#2563EB",
    "informativa":"#6B7280",
    "positivo":   "#059669",
    "fundo":      "#F8FAFC",
    "titulo":     "#0F172A",
    "subtitulo":  "#334155",
    "borda":      "#CBD5E1",
    "header_bg":  "#1E3A5F",
    "header_fg":  "#FFFFFF",
    "link":       "#1D4ED8",
}

def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16)/255 for i in (0, 2, 4))

SEV_COLOR = {
    "Critica":    COR["critica"],
    "Alta":       COR["alta"],
    "Media":      COR["media"],
    "Baixa":      COR["baixa"],
    "Informativa":COR["informativa"],
}

HERE = Path(__file__).parent
DATA = HERE / "findings.json"
OUT  = HERE / "relatorio-auditoria-seguranca.pdf"

# ── Carrega dados ─────────────────────────────────────────────────────────
with open(DATA, encoding="utf-8") as f:
    D = json.load(f)

ACHADOS      = D["achados"]
PONTOS_FORTES = D["pontos_fortes"]
PROJETO      = D["projeto"]
DATA_REL     = D["data"]
VERSAO       = D["versao_relatorio"]
AUTOR        = D["autor"]

# Contagem por severidade
sev_count = {}
for a in ACHADOS:
    s = a["severidade"]
    sev_count[s] = sev_count.get(s, 0) + 1

cat_count = {}
for a in ACHADOS:
    c = a["categoria"].split(" - ")[0]
    cat_count[c] = cat_count.get(c, 0) + 1

# ── Estilos ───────────────────────────────────────────────────────────────
def build_styles():
    base = getSampleStyleSheet()
    styles = {}

    def add(name, **kw):
        styles[name] = ParagraphStyle(name, **kw)

    add("cover_title",   fontName="Helvetica-Bold", fontSize=28, textColor=colors.HexColor(COR["header_fg"]),
        alignment=TA_CENTER, leading=34, spaceAfter=6)
    add("cover_sub",     fontName="Helvetica", fontSize=14, textColor=colors.HexColor(COR["header_fg"]),
        alignment=TA_CENTER, leading=20, spaceAfter=4)
    add("cover_meta",    fontName="Helvetica", fontSize=11, textColor=colors.HexColor("#CBD5E1"),
        alignment=TA_CENTER, leading=16)
    add("cover_conf",    fontName="Helvetica-Bold", fontSize=13, textColor=colors.HexColor("#FCA5A5"),
        alignment=TA_CENTER, leading=18, spaceBefore=12)

    add("h1",  fontName="Helvetica-Bold", fontSize=16, textColor=colors.HexColor(COR["titulo"]),
        spaceBefore=18, spaceAfter=8, leading=20,
        borderPad=4, borderColor=colors.HexColor(COR["header_bg"]),
        borderWidth=0, leftIndent=0)
    add("h2",  fontName="Helvetica-Bold", fontSize=13, textColor=colors.HexColor(COR["subtitulo"]),
        spaceBefore=14, spaceAfter=6, leading=17)
    add("h3",  fontName="Helvetica-Bold", fontSize=11, textColor=colors.HexColor(COR["subtitulo"]),
        spaceBefore=10, spaceAfter=4, leading=15)
    add("body", fontName="Helvetica", fontSize=10, textColor=colors.HexColor(COR["titulo"]),
        leading=15, spaceAfter=4, alignment=TA_JUSTIFY)
    add("body_left", fontName="Helvetica", fontSize=10, textColor=colors.HexColor(COR["titulo"]),
        leading=15, spaceAfter=4)
    add("code", fontName="Courier", fontSize=8.5, textColor=colors.HexColor("#1E293B"),
        backColor=colors.HexColor("#F1F5F9"), leading=12, spaceAfter=4,
        leftIndent=8, rightIndent=8, borderPad=4)
    add("bullet", fontName="Helvetica", fontSize=10, textColor=colors.HexColor(COR["titulo"]),
        leading=14, spaceAfter=2, leftIndent=16, bulletIndent=4)
    add("label",  fontName="Helvetica-Bold", fontSize=9, textColor=colors.HexColor(COR["subtitulo"]),
        leading=12, spaceAfter=1)
    add("small",  fontName="Helvetica", fontSize=8.5, textColor=colors.HexColor("#64748B"),
        leading=11, spaceAfter=2)
    add("toc_h1", fontName="Helvetica-Bold", fontSize=11, textColor=colors.HexColor(COR["link"]),
        leading=15, spaceAfter=2)
    add("toc_h2", fontName="Helvetica", fontSize=10, textColor=colors.HexColor(COR["subtitulo"]),
        leading=13, spaceAfter=1, leftIndent=16)
    add("exec_risk", fontName="Helvetica-Bold", fontSize=11, textColor=colors.HexColor(COR["titulo"]),
        leading=15, spaceAfter=4)
    return styles

ST = build_styles()

# ── Gráficos ──────────────────────────────────────────────────────────────
def grafico_rosca():
    labels = [k for k in ["Media","Baixa","Informativa"] if k in sev_count]
    sizes  = [sev_count[k] for k in labels]
    cores  = [SEV_COLOR[k] for k in labels]
    if not sizes:
        return None
    fig, ax = plt.subplots(figsize=(4.2, 3.2))
    wedges, texts, autotexts = ax.pie(
        sizes, labels=None, colors=cores, autopct="%d", startangle=90,
        wedgeprops=dict(width=0.55, edgecolor="white", linewidth=1.5),
        pctdistance=0.75, textprops={"fontsize": 11, "fontweight": "bold", "color": "white"}
    )
    for at in autotexts:
        at.set_fontsize(10)
        at.set_color("white")
        at.set_fontweight("bold")
    ax.set_title("Achados por Severidade", fontsize=11, fontweight="bold", pad=8, color="#0F172A")
    patches = [mpatches.Patch(color=SEV_COLOR[l], label=f"{l} ({sev_count[l]})") for l in labels]
    ax.legend(handles=patches, loc="lower center", bbox_to_anchor=(0.5, -0.18),
              ncol=2, fontsize=8.5, frameon=False)
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format="png", dpi=130, bbox_inches="tight", transparent=False,
                facecolor="white")
    plt.close(fig)
    buf.seek(0)
    return buf

def grafico_barras():
    cats  = list(cat_count.keys())
    vals  = [cat_count[c] for c in cats]
    cores_bar = ["#2563EB","#D97706","#059669","#6B7280","#EA580C"][:len(cats)]
    fig, ax = plt.subplots(figsize=(5.5, 3.0))
    bars = ax.barh(cats, vals, color=cores_bar, edgecolor="white", height=0.55)
    ax.set_xlabel("Quantidade de achados", fontsize=9)
    ax.set_title("Achados por Categoria", fontsize=11, fontweight="bold", pad=6, color="#0F172A")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.tick_params(axis="y", labelsize=8.5)
    ax.tick_params(axis="x", labelsize=8.5)
    ax.set_xlim(0, max(vals) + 1.2)
    for bar, v in zip(bars, vals):
        ax.text(bar.get_width() + 0.05, bar.get_y() + bar.get_height()/2,
                str(v), va="center", ha="left", fontsize=9, fontweight="bold", color="#0F172A")
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format="png", dpi=130, bbox_inches="tight", transparent=False,
                facecolor="white")
    plt.close(fig)
    buf.seek(0)
    return buf

def grafico_matriz():
    """Matriz de risco 3x3: Probabilidade x Impacto."""
    fig, ax = plt.subplots(figsize=(4.8, 3.5))
    grade = [
        ["#86EFAC","#FDE68A","#FCA5A5"],
        ["#FDE68A","#FCA5A5","#FCA5A5"],
        ["#86EFAC","#FDE68A","#FCA5A5"],
    ]
    ylabels = ["Alta","Média","Baixa"]
    xlabels = ["Baixo","Médio","Alto"]
    for r in range(3):
        for c in range(3):
            rect = plt.Rectangle([c, r], 1, 1, color=grade[r][c], ec="white", lw=1.5)
            ax.add_patch(rect)
    # Posiciona achados
    pontos = {
        "SEC-001": (1.5, 1.5),
        "SEC-002": (1.5, 1.5),
        "SEC-003": (0.5, 1.5),
        "SEC-004": (0.5, 0.5),
        "SEC-005": (0.5, 0.5),
        "SEC-006": (0.5, 0.3),
        "SEC-007": (0.5, 0.7),
        "SEC-008": (0.5, 0.5),
    }
    plotados = {}
    for sec, (x, y) in pontos.items():
        key = (round(x,1), round(y,1))
        offset = plotados.get(key, 0) * 0.15
        plotados[key] = plotados.get(key, 0) + 1
        ax.annotate(sec.replace("SEC-","S"), (x + offset, y + offset),
                    fontsize=7, ha="center", va="center",
                    bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="#94A3B8", alpha=0.85))
    ax.set_xlim(0, 3); ax.set_ylim(0, 3)
    ax.set_xticks([0.5, 1.5, 2.5]); ax.set_xticklabels(xlabels, fontsize=9)
    ax.set_yticks([0.5, 1.5, 2.5]); ax.set_yticklabels(ylabels, fontsize=9)
    ax.set_xlabel("Impacto", fontsize=9, fontweight="bold")
    ax.set_ylabel("Probabilidade", fontsize=9, fontweight="bold")
    ax.set_title("Matriz de Risco", fontsize=11, fontweight="bold", pad=6, color="#0F172A")
    plt.tight_layout()
    buf = io.BytesIO()
    plt.savefig(buf, format="png", dpi=130, bbox_inches="tight", transparent=False,
                facecolor="white")
    plt.close(fig)
    buf.seek(0)
    return buf

# ── Helpers PDF ───────────────────────────────────────────────────────────
def chip_sev(sev):
    cor = SEV_COLOR.get(sev, COR["informativa"])
    return f'<font color="{cor}"><b>[{sev.upper()}]</b></font>'

def hr():
    return HRFlowable(width="100%", thickness=0.5, color=colors.HexColor(COR["borda"]),
                      spaceAfter=6, spaceBefore=6)

def sec_title(txt):
    return [
        Paragraph(txt, ST["h1"]),
        HRFlowable(width="100%", thickness=1.5, color=colors.HexColor(COR["header_bg"]),
                   spaceAfter=8, spaceBefore=0),
    ]

def field(label, value, style="body_left"):
    return [
        Paragraph(esc(label), ST["label"]),
        Paragraph(esc(str(value)), ST[style]),
    ]

def esc(txt):
    """Escapa texto puro para uso seguro em Paragraph (sem interpretar tags)."""
    return str(txt).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def code_block(txt):
    safe = esc(txt)
    return Paragraph(safe, ST["code"])

def bullet(txt):
    safe = esc(txt)
    return Paragraph(f"• {safe}", ST["bullet"])

# ── Header / Footer ───────────────────────────────────────────────────────
class DocCanvas:
    def __init__(self, title):
        self.title = title

    def on_page(self, c, doc):
        c.saveState()
        w, h = A4
        # Header
        c.setFillColor(colors.HexColor(COR["header_bg"]))
        c.rect(0, h - 1.1*cm, w, 1.1*cm, fill=1, stroke=0)
        c.setFillColor(colors.white)
        c.setFont("Helvetica-Bold", 8)
        c.drawString(1.8*cm, h - 0.75*cm, self.title)
        c.setFont("Helvetica", 8)
        c.drawRightString(w - 1.8*cm, h - 0.75*cm, "CONFIDENCIAL")
        # Footer
        c.setFillColor(colors.HexColor(COR["borda"]))
        c.rect(0, 0, w, 0.9*cm, fill=1, stroke=0)
        c.setFillColor(colors.HexColor(COR["subtitulo"]))
        c.setFont("Helvetica", 8)
        c.drawString(1.8*cm, 0.32*cm, f"Auditoria de Segurança — {PROJETO} — v{VERSAO} — {DATA_REL}")
        c.drawRightString(w - 1.8*cm, 0.32*cm, f"Página {doc.page}")
        c.restoreState()

    def on_cover(self, c, doc):
        c.saveState()
        w, h = A4
        # fundo gradiente simulado via dois retangulos
        c.setFillColor(colors.HexColor(COR["header_bg"]))
        c.rect(0, 0, w, h, fill=1, stroke=0)
        c.setFillColor(colors.HexColor("#0A2540"))
        c.rect(0, 0, w, h * 0.38, fill=1, stroke=0)
        c.restoreState()

# ── Capa ──────────────────────────────────────────────────────────────────
def build_cover():
    story = []
    story.append(Spacer(1, 3.5*cm))
    story.append(Paragraph("Relatório de Auditoria de Segurança", ST["cover_title"]))
    story.append(Paragraph(f"Projeto: {PROJETO}", ST["cover_sub"]))
    story.append(Spacer(1, 0.6*cm))

    meta = [
        ["Versão:", VERSAO],
        ["Data:", DATA_REL],
        ["Autor:", AUTOR],
        ["Tipo de análise:", "Estática • White-box • Manual"],
        ["Escopo:", "Backend (Java/Spring Boot) · Frontend (Angular) · Banco (SQL Server) · CI/CD · Docker"],
    ]
    t = Table(meta, colWidths=[4*cm, 11*cm])
    t.setStyle(TableStyle([
        ("FONTNAME",  (0,0),(-1,-1), "Helvetica"),
        ("FONTSIZE",  (0,0),(-1,-1), 10),
        ("FONTNAME",  (0,0),(0,-1),  "Helvetica-Bold"),
        ("TEXTCOLOR", (0,0),(-1,-1), colors.HexColor("#CBD5E1")),
        ("TEXTCOLOR", (1,0),(1,-1),  colors.white),
        ("ROWBACKGROUNDS", (0,0),(-1,-1), [None]),
        ("VALIGN",    (0,0),(-1,-1), "TOP"),
        ("TOPPADDING",(0,0),(-1,-1), 4),
        ("BOTTOMPADDING",(0,0),(-1,-1), 4),
    ]))
    story.append(t)
    story.append(Spacer(1, 1.2*cm))
    story.append(Paragraph("⚠  CLASSIFICAÇÃO: CONFIDENCIAL", ST["cover_conf"]))
    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph(
        "Este documento contém informações sensíveis sobre a postura de segurança do sistema. "
        "Acesso restrito à equipe de desenvolvimento e gestão autorizada.",
        ST["cover_meta"]))
    story.append(NextPageTemplate("normal"))
    story.append(PageBreak())
    return story

# ── Sumário ───────────────────────────────────────────────────────────────
def build_toc():
    story = sec_title("Sumário")
    sections = [
        ("1. Resumo Executivo", None),
        ("2. Escopo, Metodologia e Limitações", None),
        ("3. Pontos Fortes", None),
        ("4. Achados Detalhados", None),
        ("   4.1  SEC-001 — Swagger UI exposto por padrão", None),
        ("   4.2  SEC-002 — Actuator sem restrição de detalhes", None),
        ("   4.3  SEC-003 — Grace period de refresh token", None),
        ("   4.4  SEC-004 — X-Forwarded-For sem proxy confiável", None),
        ("   4.5  SEC-005 — Validação de segredo incompleta", None),
        ("   4.6  SEC-006 — CSP com unsafe-inline em style-src", None),
        ("   4.7  SEC-007 — Rate limiting ausente em /notas", None),
        ("   4.8  SEC-008 — Versão Spring Boot inexistente", None),
        ("5. Recomendações Priorizadas", None),
        ("6. Plano de Reteste", None),
        ("7. Anexos", None),
        ("8. Issues para o GitHub", None),
    ]
    for title, _ in sections:
        indent = title.startswith("   ")
        p_style = ST["toc_h2"] if indent else ST["toc_h1"]
        story.append(Paragraph(title.strip(), p_style))
    story.append(PageBreak())
    return story

# ── Resumo Executivo ──────────────────────────────────────────────────────
def build_exec():
    story = sec_title("1. Resumo Executivo")

    story.append(Paragraph(
        "O projeto GENERICO demonstra uma <b>postura de segurança acima da média</b> para um template de aplicação "
        "web. Os mecanismos fundamentais — isolamento de tenant em dupla camada, armazenamento de segredos apenas "
        "como hash, criptografia em repouso do segredo TOTP, proteção de sessão com refresh token HttpOnly e "
        "revogação imediata — estão implementados corretamente e com atenção ao detalhe. "
        "Não foram identificadas vulnerabilidades críticas ou altas exploráveis diretamente a partir da análise "
        "do código-fonte.",
        ST["body"]))
    story.append(Spacer(1, 0.3*cm))

    story.append(Paragraph(
        "Os <b>8 achados identificados</b> distribuem-se em faixas de risco médio a informativo, com ênfase em "
        "configurações de ambiente (Swagger/Actuator expostos, headers de proxy) que podem tornar-se problemas "
        "reais em um deploy de produção descuidado. O risco de negócio mais relevante é a exposição acidental "
        "do contrato completo da API e de informações de infraestrutura, que facilitam reconhecimento para "
        "ataques futuros.",
        ST["body"]))
    story.append(Spacer(1, 0.3*cm))

    # Tabela de contagem
    sev_rows = [["Severidade", "Qtd", "Exemplos"]]
    sev_data = [
        ("Crítica",    "#B91C1C", "—"),
        ("Alta",       "#EA580C", "—"),
        ("Média",      "#D97706", "SEC-001, SEC-002, SEC-003"),
        ("Baixa",      "#2563EB", "SEC-004, SEC-005, SEC-007"),
        ("Informativa","#6B7280", "SEC-006, SEC-008"),
    ]
    for sev, cor, ex in sev_data:
        qtd = sev_count.get(sev, 0)
        sev_rows.append([
            Paragraph(f'<font color="{cor}"><b>{sev}</b></font>', ST["body_left"]),
            Paragraph(f'<font color="{cor}"><b>{qtd}</b></font>', ST["body_left"]),
            Paragraph(ex if qtd > 0 else "—", ST["small"]),
        ])
    sev_table = Table(sev_rows, colWidths=[3.5*cm, 2*cm, 9*cm])
    sev_table.setStyle(TableStyle([
        ("BACKGROUND", (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",  (0,0),(-1,0), colors.white),
        ("FONTNAME",   (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTSIZE",   (0,0),(-1,0), 9),
        ("ROWBACKGROUNDS",(0,1),(-1,-1), [colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",       (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING", (0,0),(-1,-1), 5),
        ("BOTTOMPADDING",(0,0),(-1,-1), 5),
        ("LEFTPADDING",(0,0),(-1,-1), 8),
        ("VALIGN",     (0,0),(-1,-1), "MIDDLE"),
    ]))
    story.append(sev_table)
    story.append(Spacer(1, 0.5*cm))

    # Gráficos lado a lado
    buf_rosca = grafico_rosca()
    buf_barras = grafico_barras()
    buf_matriz = grafico_matriz()

    if buf_rosca and buf_barras:
        imgs = [[Image(buf_rosca, width=6.5*cm, height=5*cm),
                 Image(buf_barras, width=8*cm, height=5*cm)]]
        ig = Table(imgs, colWidths=[8*cm, 9*cm])
        ig.setStyle(TableStyle([("VALIGN",(0,0),(-1,-1),"MIDDLE"),
                                ("ALIGN", (0,0),(-1,-1),"CENTER")]))
        story.append(ig)
        story.append(Spacer(1, 0.4*cm))

    if buf_matriz:
        story.append(Spacer(1, 0.2*cm))
        story.append(Image(buf_matriz, width=10*cm, height=7*cm))
        story.append(Spacer(1, 0.4*cm))

    story.append(Paragraph("<b>3 Ações mais urgentes (P1/P2):</b>", ST["h3"]))
    urgentes = [
        "Desabilitar Swagger UI em produção — definir SWAGGER_ENABLED=false como default e restringir no código (SEC-001).",
        "Adicionar management.endpoint.health.show-details=never no application.yml de produção (SEC-002).",
        "Expandir a validação de segredos de exemplo para MFA_ENCRYPTION_KEY na inicialização da aplicação (SEC-005).",
    ]
    for u in urgentes:
        story.append(bullet(u))
    story.append(PageBreak())
    return story

# ── Escopo e Metodologia ──────────────────────────────────────────────────
def build_scope():
    story = sec_title("2. Escopo, Metodologia e Limitações")

    story.append(Paragraph("<b>Dentro do escopo</b>", ST["h2"]))
    in_scope = [
        "Backend Java/Spring Boot: todos os pacotes em com.example.app (auth, security, tenant, audit, nota, shared, mail, config, user)",
        "Frontend Angular: src/app (core, features, layout, shared), configuração nginx",
        "Infraestrutura: docker-compose.yml, docker-compose.https.yml, Dockerfiles, scripts de banco (database/)",
        "CI/CD: .github/workflows/ci.yml, .trivyignore.yaml, dependências (pom.xml, package.json)",
    ]
    for i in in_scope:
        story.append(bullet(i))

    story.append(Paragraph("<b>Fora do escopo</b>", ST["h2"]))
    out_scope = [
        "Infraestrutura em execução, ambientes de produção/homologação",
        "Testes dinâmicos (DAST), engenharia social, segurança física",
        "Serviços de terceiros (Mailpit, GitHub Actions runners)",
        "Diretórios excluídos: node_modules/, target/, .git/",
    ]
    for o in out_scope:
        story.append(bullet(o))
    story.append(Paragraph("<b>Stack detectada</b>", ST["h2"]))
    stack = [
        ["Camada", "Tecnologia"],
        ["Backend", "Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA / Hibernate 6"],
        ["Banco", "SQL Server 2022 · multi-tenancy por coluna discriminadora (@TenantId)"],
        ["Autenticação", "JWT (JJWT 0.12.6) · BCrypt · TOTP (RFC 6238) · Refresh token opaco HttpOnly"],
        ["Frontend", "Angular 22 (standalone, signals, TypeScript strict) · Angular Material · Vitest"],
        ["Infra", "Docker Compose · nginx 1.31 · GitHub Actions · Trivy 0.58.0"],
    ]
    t = Table(stack, colWidths=[4*cm, 12.5*cm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",  (0,0),(-1,0), colors.white),
        ("FONTNAME",   (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTSIZE",   (0,0),(-1,-1), 9),
        ("ROWBACKGROUNDS",(0,1),(-1,-1), [colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",       (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING", (0,0),(-1,-1), 5),
        ("BOTTOMPADDING",(0,0),(-1,-1), 5),
        ("LEFTPADDING",(0,0),(-1,-1), 8),
        ("VALIGN",     (0,0),(-1,-1), "TOP"),
        ("FONTNAME",   (0,1),(0,-1), "Helvetica-Bold"),
    ]))
    story.append(t)
    story.append(Spacer(1, 0.4*cm))

    story.append(Paragraph("<b>Critério de severidade</b>", ST["h2"]))
    story.append(Paragraph(
        "Cada achado recebe um vetor CVSS v3.1 completo. O score base é ajustado pelo contexto: "
        "rotas acessíveis apenas a ADMIN, necessidade de autenticação prévia, ou mitigações compensatórias "
        "já presentes (ex: rate limiting, SameSite=Strict) são fatores de redução explicitamente declarados.",
        ST["body"]))

    story.append(Paragraph("<b>Limitações e premissas</b>", ST["h2"]))
    lims = [
        "application.yml de produção não disponível: configurações como management.endpoint.health.show-details não puderam ser verificadas.",
        "Histórico git não foi varrido por segredos (requer git log --all -p — fora do escopo desta análise estática).",
        "Testes dinâmicos (DAST) não foram executados: algumas vulnerabilidades de configuração só são confirmáveis em runtime.",
        "Repositório Maven interno não verificado: versão 3.5.16 do Spring Boot não localizada no Maven Central.",
        "Configuração real de produção (WAF, firewall, HTTPS) não disponível para verificação.",
    ]
    for l in lims:
        story.append(bullet(l))
    story.append(PageBreak())
    return story

# ── Pontos Fortes ─────────────────────────────────────────────────────────
def build_strengths():
    story = sec_title("3. Pontos Fortes")
    story.append(Paragraph(
        "Os controles abaixo foram verificados no código e estão corretamente implementados. "
        "Eles representam a base sólida de segurança do projeto.",
        ST["body"]))
    story.append(Spacer(1, 0.3*cm))

    for ps in PONTOS_FORTES:
        row = [
            [Paragraph(f'<font color="{COR["positivo"]}">&#10003;</font> <b>{esc(ps["id"])}</b>', ST["body_left"]),
             Paragraph(f'<b>{esc(ps["descricao"])}</b>', ST["body_left"])],
            [Paragraph("Evidência:", ST["label"]),
             Paragraph(esc(ps["evidencia"]), ST["small"])],
            [Paragraph("Detalhe:", ST["label"]),
             Paragraph(esc(ps["detalhe"]), ST["small"])],
        ]
        inner = Table(row, colWidths=[3.5*cm, 12*cm])
        inner.setStyle(TableStyle([
            ("VALIGN",      (0,0),(-1,-1), "TOP"),
            ("TOPPADDING",  (0,0),(-1,-1), 3),
            ("BOTTOMPADDING",(0,0),(-1,-1), 3),
            ("LEFTPADDING", (0,0),(-1,-1), 4),
            ("SPAN",        (0,0),(0,0)),
        ]))
        wrapper = Table([[inner]], colWidths=[16*cm])
        wrapper.setStyle(TableStyle([
            ("BACKGROUND",   (0,0),(-1,-1), colors.HexColor("#F0FDF4")),
            ("LINEAFTER",    (0,0),(0,-1),  0.5, colors.HexColor(COR["positivo"])),
            ("LEFTPADDING",  (0,0),(-1,-1), 6),
            ("TOPPADDING",   (0,0),(-1,-1), 4),
            ("BOTTOMPADDING",(0,0),(-1,-1), 4),
            ("BOX",          (0,0),(-1,-1), 0.5, colors.HexColor(COR["borda"])),
        ]))
        story.append(wrapper)
        story.append(Spacer(1, 0.2*cm))
    story.append(PageBreak())
    return story

# ── Achados detalhados ────────────────────────────────────────────────────
def build_finding(a, idx):
    sev = a["severidade"]
    cor = SEV_COLOR.get(sev, COR["informativa"])
    story = []

    # Cabeçalho do achado
    hdr_data = [[
        Paragraph(f'<font color="white"><b>{a["id"]}</b></font>', ST["body_left"]),
        Paragraph(f'<font color="white">{a["titulo"]}</font>', ST["body_left"]),
        Paragraph(f'<font color="white"><b>{sev.upper()}</b></font>', ST["body_left"]),
    ]]
    hdr = Table(hdr_data, colWidths=[2.5*cm, 10.5*cm, 3*cm])
    hdr.setStyle(TableStyle([
        ("BACKGROUND",   (0,0),(-1,-1), colors.HexColor(cor)),
        ("VALIGN",       (0,0),(-1,-1), "MIDDLE"),
        ("TOPPADDING",   (0,0),(-1,-1), 6),
        ("BOTTOMPADDING",(0,0),(-1,-1), 6),
        ("LEFTPADDING",  (0,0),(-1,-1), 8),
        ("ALIGN",        (2,0),(2,0),   "RIGHT"),
        ("RIGHTPADDING", (2,0),(2,0),   8),
    ]))
    story.append(hdr)

    # Metadados
    meta_rows = [
        ["Categoria", a["categoria"], "CVSS Score", str(a["cvss_score"])],
        ["CWE",       a["cwe"],       "Vetor CVSS", a["cvss_vector"]],
        ["OWASP",     a["owasp"],     "Confiança",  a["confianca"]],
        ["Prioridade",a["p_prioridade"], "Arquivos", "\n".join(a["arquivos"])],
    ]
    meta_t = Table(meta_rows, colWidths=[2.8*cm, 5.7*cm, 2.8*cm, 5.2*cm])
    meta_t.setStyle(TableStyle([
        ("FONTNAME",    (0,0),(0,-1), "Helvetica-Bold"),
        ("FONTNAME",    (2,0),(2,-1), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0),(-1,-1), 8.5),
        ("TEXTCOLOR",   (0,0),(0,-1), colors.HexColor(COR["subtitulo"])),
        ("TEXTCOLOR",   (2,0),(2,-1), colors.HexColor(COR["subtitulo"])),
        ("ROWBACKGROUNDS",(0,0),(-1,-1),[colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",        (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING",  (0,0),(-1,-1), 4),
        ("BOTTOMPADDING",(0,0),(-1,-1), 4),
        ("LEFTPADDING", (0,0),(-1,-1), 6),
        ("VALIGN",      (0,0),(-1,-1), "TOP"),
    ]))
    story.append(meta_t)
    story.append(Spacer(1, 0.25*cm))

    def lbl_val(lbl, val, code=False):
        story.append(Paragraph(esc(lbl), ST["label"]))
        if code:
            story.append(code_block(val))
        else:
            story.append(Paragraph(esc(val), ST["body"]))
        story.append(Spacer(1, 0.1*cm))

    lbl_val("Descrição", a["descricao"])
    lbl_val("Evidência (arquivo:linha)", a["evidencia"], code=True)
    lbl_val("Cenário de exploração", a["cenario"])
    lbl_val("Pré-condições", a["preconditions"])
    lbl_val("Impacto", a["impacto"])
    lbl_val("Correção sugerida", a["correcao"])

    story.append(Spacer(1, 0.4*cm))
    story.append(HRFlowable(width="100%", thickness=0.5,
                             color=colors.HexColor(COR["borda"]),
                             spaceAfter=8, spaceBefore=4))
    return story

def build_findings():
    story = sec_title("4. Achados Detalhados")

    # Tabela resumo
    hdr = [["ID", "Sev.", "CVSS", "Arquivo principal", "Título"]]
    for a in ACHADOS:
        sev = a["severidade"]
        cor = SEV_COLOR.get(sev, COR["informativa"])
        hdr.append([
            Paragraph(f'<b>{a["id"]}</b>', ST["small"]),
            Paragraph(f'<font color="{cor}"><b>{sev}</b></font>', ST["small"]),
            Paragraph(str(a["cvss_score"]), ST["small"]),
            Paragraph(a["arquivos"][0], ST["small"]),
            Paragraph(a["titulo"], ST["small"]),
        ])
    t = Table(hdr, colWidths=[1.8*cm, 2.2*cm, 1.5*cm, 5.5*cm, 5.5*cm])
    t.setStyle(TableStyle([
        ("BACKGROUND",  (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",   (0,0),(-1,0), colors.white),
        ("FONTNAME",    (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0),(-1,0), 8.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",        (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING",  (0,0),(-1,-1), 4),
        ("BOTTOMPADDING",(0,0),(-1,-1), 4),
        ("LEFTPADDING", (0,0),(-1,-1), 6),
        ("VALIGN",      (0,0),(-1,-1), "MIDDLE"),
    ]))
    story.append(t)
    story.append(Spacer(1, 0.5*cm))

    for i, a in enumerate(ACHADOS, 1):
        story.append(Paragraph(f"4.{i}  {a['id']} — {a['titulo']}", ST["h2"]))
        for elem in build_finding(a, i):
            story.append(elem)

    story.append(PageBreak())
    return story

# ── Recomendações ─────────────────────────────────────────────────────────
def build_recs():
    story = sec_title("5. Recomendações Priorizadas")

    prioridades = {
        "P1": ("Imediato (< 1 semana)", "#B91C1C", []),
        "P2": ("Curto prazo (< 1 mês)", "#D97706", []),
        "P3": ("Médio prazo (< 3 meses)", "#2563EB", []),
    }
    for a in ACHADOS:
        p = a["p_prioridade"]
        if p in prioridades:
            prioridades[p][2].append(a)

    for p, (desc, cor, lista) in prioridades.items():
        story.append(Paragraph(
            f'<font color="{cor}"><b>{p} — {desc}</b></font>', ST["h2"]))
        if not lista:
            story.append(Paragraph("Nenhum achado nesta prioridade.", ST["body_left"]))
        for a in lista:
            esforco = {"P1":"—", "P2":"Baixo/Médio", "P3":"Médio"}[p]
            story.append(Paragraph(
                f'<b>{a["id"]}</b> · {a["titulo"]} · Esforço: {esforco}',
                ST["h3"]))
            story.append(Paragraph(a["correcao"], ST["body"]))
            story.append(Spacer(1, 0.2*cm))
        story.append(Spacer(1, 0.2*cm))

    story.append(Paragraph("<b>Quick wins destacados:</b>", ST["h3"]))
    qw = [
        "SEC-001: Uma variável de ambiente (SWAGGER_ENABLED=false). Impacto imediato, esforço mínimo.",
        "SEC-002: Duas linhas no application.yml. Zero risco de regressão.",
        "SEC-005: Adicionar verificação de 'troque' em AppProperties.Mfa — < 5 linhas de código.",
    ]
    for q in qw:
        story.append(bullet(q))
    story.append(PageBreak())
    return story

# ── Plano de reteste ──────────────────────────────────────────────────────
def build_retest():
    story = sec_title("6. Plano de Reteste")
    story.append(Paragraph(
        "Para cada achado, o critério abaixo define como validar objetivamente que a correção foi aplicada.",
        ST["body"]))
    story.append(Spacer(1, 0.3*cm))

    criterios = [
        ("SEC-001", "GET /swagger-ui.html e GET /v3/api-docs sem token retornam HTTP 404 ou 403 em produção."),
        ("SEC-002", "GET /actuator/health retorna apenas {\"status\":\"UP\"} sem subitens de componentes ou detalhes de banco."),
        ("SEC-003", "Documentar o valor de app.refresh.reuse-grace-seconds e registrar como risco aceito na ADR. Opcional: monitorar eventos REFRESH_REUSO na auditoria."),
        ("SEC-004", "Confirmar que em produção o backend não está acessível diretamente (apenas via nginx/load balancer). Verificar configuração de InternalProxies no Tomcat."),
        ("SEC-005", "Iniciar a aplicação com MFA_ENCRYPTION_KEY contendo a palavra 'troque': deve falhar na startup com mensagem descritiva."),
        ("SEC-006", "Verificar o header Content-Security-Policy na resposta do nginx: style-src não contém 'unsafe-inline'."),
        ("SEC-007", "Confirmar que POST /api/v1/notas retorna HTTP 429 após exceder o limite de requisições configurado."),
        ("SEC-008", "Build CI sem cache Maven completa com sucesso e usa a versão correta do Spring Boot."),
    ]

    rows = [["ID", "Critério de aceite"]]
    for sec_id, crit in criterios:
        rows.append([
            Paragraph(f"<b>{sec_id}</b>", ST["small"]),
            Paragraph(crit, ST["small"]),
        ])
    t = Table(rows, colWidths=[2.2*cm, 14.3*cm])
    t.setStyle(TableStyle([
        ("BACKGROUND",  (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",   (0,0),(-1,0), colors.white),
        ("FONTNAME",    (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0),(-1,-1), 9),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",        (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING",  (0,0),(-1,-1), 5),
        ("BOTTOMPADDING",(0,0),(-1,-1), 5),
        ("LEFTPADDING", (0,0),(-1,-1), 8),
        ("VALIGN",      (0,0),(-1,-1), "TOP"),
    ]))
    story.append(t)
    story.append(Spacer(1, 0.5*cm))
    story.append(Paragraph(
        "Recomenda-se reexecutar esta auditoria completa após a aplicação das correções P1 e P2, "
        "e executar testes dinâmicos (DAST) em ambiente de homologação antes de qualquer deploy em produção.",
        ST["body"]))
    story.append(PageBreak())
    return story

# ── Anexos ────────────────────────────────────────────────────────────────
def build_annexes():
    story = sec_title("7. Anexos")

    story.append(Paragraph("<b>A — Tabela de Cobertura de Rotas/Handlers</b>", ST["h2"]))
    story.append(Paragraph(
        "Todas as rotas identificadas, com status de verificação de autenticação, autorização, "
        "verificação de posse/tenant, validação de entrada e resultado da auditoria.",
        ST["small"]))
    story.append(Spacer(1, 0.2*cm))

    cob_hdr = [["Handler / Rota", "Autent.", "Autorização", "Tenant/Posse", "Validação", "Status"]]
    cobertura = [
        ("POST /api/v1/auth/register",          "Não",   "Pública",          "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/login",             "Não",   "Pública",          "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/mfa/verify",        "Não",   "MFA token",        "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/refresh",           "Cookie","X-Requested-With", "N/A",         "Cookie presente", "✓ Verificado"),
        ("POST /api/v1/auth/logout",            "Opc.", "X-Requested-With", "N/A",         "—",               "✓ Verificado"),
        ("POST /api/v1/auth/logout-all",        "JWT",   "Autenticado",      "N/A",         "—",               "✓ Verificado"),
        ("GET  /api/v1/auth/me",                "JWT",   "Autenticado",      "N/A",         "—",               "✓ Verificado"),
        ("POST /api/v1/auth/forgot-password",   "Não",   "Pública",          "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/reset-password",    "Não",   "Token de ação",    "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/verify-email",      "Não",   "Token de ação",    "N/A",         "Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/resend-verification","Não",  "Pública",          "N/A",         "Bean Validation", "✓ Verificado"),
        ("GET  /api/v1/auth/config",            "Não",   "Pública",          "N/A",         "—",               "✓ Verificado"),
        ("POST /api/v1/auth/mfa/setup",         "JWT",   "Autenticado",      "userId token","—",               "✓ Verificado"),
        ("POST /api/v1/auth/mfa/enable",        "JWT",   "Autenticado",      "userId token","Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/mfa/disable",       "JWT",   "Autenticado",      "userId token","Bean Validation", "✓ Verificado"),
        ("POST /api/v1/auth/change-password",   "JWT",   "Autenticado",      "userId token","Bean Validation", "✓ Verificado"),
        ("GET  /api/v1/notas",                  "JWT",   "Autenticado",      "@TenantId",   "—",               "✓ Verificado"),
        ("GET  /api/v1/notas/{id}",             "JWT",   "Dono ou ADMIN",    "@TenantId",   "—",               "✓ Verificado"),
        ("POST /api/v1/notas",                  "JWT",   "Autenticado",      "@TenantId",   "Bean Validation", "✓ Verificado"),
        ("DELETE /api/v1/notas/{id}",           "JWT",   "Dono ou ADMIN",    "@TenantId",   "—",               "✓ Verificado"),
        ("GET  /api/v1/admin/auditoria",        "JWT",   "ROLE_ADMIN",       "tenantId token","Paginação limitada","✓ Verificado"),
        ("GET  /actuator/health/**",            "Não",   "Pública",          "N/A",         "—",               "⚠ SEC-002"),
        ("GET  /swagger-ui/**",                 "Não",   "Pública",          "N/A",         "—",               "⚠ SEC-001"),
        ("GET  /v3/api-docs/**",                "Não",   "Pública",          "N/A",         "—",               "⚠ SEC-001"),
    ]
    for row in cobertura:
        ok = "⚠" in row[5]
        cor_bg = colors.HexColor("#FEF9C3") if ok else colors.white
        cob_hdr.append([Paragraph(esc(c), ST["small"]) for c in row])

    cob_t = Table(cob_hdr, colWidths=[4.8*cm, 1.6*cm, 2.8*cm, 2.4*cm, 2.4*cm, 2.5*cm])
    cob_t.setStyle(TableStyle([
        ("BACKGROUND",  (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",   (0,0),(-1,0), colors.white),
        ("FONTNAME",    (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0),(-1,-1), 7.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",        (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING",  (0,0),(-1,-1), 3),
        ("BOTTOMPADDING",(0,0),(-1,-1), 3),
        ("LEFTPADDING", (0,0),(-1,-1), 5),
        ("VALIGN",      (0,0),(-1,-1), "TOP"),
    ]))
    story.append(cob_t)
    story.append(Spacer(1, 0.5*cm))

    story.append(Paragraph("<b>B — Categorias Não Aplicáveis</b>", ST["h2"]))
    na = [
        "C1 (Banco sem tranca): Aplicável — mecanismo verificado (@TenantId, TenantScopedRepository). Isolamento correto.",
        "C9 (SSRF/Upload/Desserialização): Não aplicável — não há endpoints de upload, requisições a URLs externas controladas pelo usuário, ou desserialização de dados não confiáveis.",
        "XSS no frontend: Não aplicável diretamente — Angular escapa por padrão. Não há uso de innerHTML/dangerouslySetInnerHTML. Único ponto: unsafe-inline em style-src (SEC-006, baixo risco).",
    ]
    for n in na:
        story.append(bullet(n))
    story.append(Spacer(1, 0.3*cm))

    story.append(Paragraph("<b>C — Itens que Requerem Validação</b>", ST["h2"]))
    rv = [
        "SEC-004: Verificar se o backend está exposto diretamente em produção (requer acesso ao ambiente).",
        "SEC-008: Verificar repositório Maven interno para confirmar existência da versão 3.5.16.",
        "Histórico git: varredura por segredos commitados (git log --all -p | truffleHog) não executada.",
        "application.yml de produção: management.endpoint.health.show-details não verificado.",
    ]
    for r in rv:
        story.append(bullet(r))
    story.append(Spacer(1, 0.3*cm))

    story.append(Paragraph("<b>D — Ferramentas Utilizadas</b>", ST["h2"]))
    tools = [
        "Análise manual de código-fonte (white-box, estática)",
        "Kiro IDE — leitura e navegação de código",
        "Python 3.12 + reportlab + matplotlib — geração deste relatório",
    ]
    for t2 in tools:
        story.append(bullet(t2))
    story.append(Spacer(1, 0.3*cm))

    story.append(Paragraph("<b>E — Glossário</b>", ST["h2"]))
    glossario = [
        ["Termo", "Definição"],
        ["CWE",    "Common Weakness Enumeration — catálogo de tipos de fraqueza de software."],
        ["CVSS",   "Common Vulnerability Scoring System — sistema de pontuação de vulnerabilidades (v3.1)."],
        ["OWASP",  "Open Web Application Security Project — referência de riscos em aplicações web."],
        ["IDOR",   "Insecure Direct Object Reference — acesso direto a objeto por ID sem verificação de posse."],
        ["TOTP",   "Time-based One-Time Password (RFC 6238) — segundo fator de autenticação por aplicativo."],
        ["MFA",    "Multi-Factor Authentication — autenticação em múltiplos fatores."],
        ["RLS",    "Row-Level Security — isolamento de dados por linha no banco de dados."],
        ["DAST",   "Dynamic Application Security Testing — teste de segurança em aplicação em execução."],
        ["WAF",    "Web Application Firewall — firewall específico para aplicações web."],
        ["HSTS",   "HTTP Strict Transport Security — força uso de HTTPS pelo navegador."],
        ["CSP",    "Content Security Policy — política de segurança de conteúdo do navegador."],
    ]
    gt = Table(glossario, colWidths=[3*cm, 13.5*cm])
    gt.setStyle(TableStyle([
        ("BACKGROUND",  (0,0),(-1,0), colors.HexColor(COR["header_bg"])),
        ("TEXTCOLOR",   (0,0),(-1,0), colors.white),
        ("FONTNAME",    (0,0),(-1,0), "Helvetica-Bold"),
        ("FONTNAME",    (0,1),(0,-1), "Helvetica-Bold"),
        ("FONTSIZE",    (0,0),(-1,-1), 8.5),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F8FAFC")]),
        ("GRID",        (0,0),(-1,-1), 0.4, colors.HexColor(COR["borda"])),
        ("TOPPADDING",  (0,0),(-1,-1), 4),
        ("BOTTOMPADDING",(0,0),(-1,-1), 4),
        ("LEFTPADDING", (0,0),(-1,-1), 6),
        ("VALIGN",      (0,0),(-1,-1), "TOP"),
    ]))
    story.append(gt)
    story.append(PageBreak())
    return story

# ── Issues GitHub ─────────────────────────────────────────────────────────
def build_issues():
    story = sec_title("8. Issues para o GitHub")
    story.append(Paragraph(
        "Texto completo de cada issue, pronto para copiar e colar no GitHub Issues. "
        "Achados informativos relacionados foram agrupados em uma única issue.",
        ST["body"]))
    story.append(Spacer(1, 0.3*cm))

    issues = [
        {
            "n": 1,
            "sec": "SEC-001",
            "titulo": "[Segurança] Swagger UI exposto publicamente por padrão (SEC-001)",
            "labels": "security, severity:medium, config",
            "corpo": """## Descrição
O endpoint `/swagger-ui.html` e `/v3/api-docs/**` estão configurados como públicos em `SecurityConfig.java` (linha 52) e o `docker-compose.yml` define `SWAGGER_ENABLED=true` como valor padrão.

## Por que é explorável
Um atacante pode acessar o contrato completo da API sem autenticação, mapeando todos os endpoints, parâmetros e schemas. Isso acelera reconhecimento e fuzzing direcionado.

## Evidência
```
// SecurityConfig.java:52
.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

// docker-compose.yml
SWAGGER_ENABLED: ${SWAGGER_ENABLED:-true}
```

## Referências
- CWE-16: Configuration
- OWASP A05:2021 – Security Misconfiguration
- CVSS: 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N)

## Impacto
Divulgação completa do contrato da API sem autenticação.

## Correção sugerida
1. Alterar default em `docker-compose.yml`: `SWAGGER_ENABLED: ${SWAGGER_ENABLED:-false}`
2. Em `SecurityConfig`, envolver o `permitAll()` em condicional `@ConditionalOnProperty`
3. Nunca habilitar em produção

## Critérios de aceite
- [ ] `GET /swagger-ui.html` sem token retorna 403 ou 404 em produção
- [ ] `GET /v3/api-docs` sem token retorna 403 ou 404 em produção
- [ ] Variável `SWAGGER_ENABLED` não está no `.env` de produção ou está explicitamente como `false`
""",
        },
        {
            "n": 2,
            "sec": "SEC-002",
            "titulo": "[Segurança] Actuator /health exposto sem restrição de detalhes (SEC-002)",
            "labels": "security, severity:medium, config",
            "corpo": """## Descrição
`/actuator/health/**` está público em `SecurityConfig.java:52` sem configuração de `show-details=never` verificada.

## Por que é explorável
Com `show-details=always` ou `when-authorized` (default Spring Boot), a resposta pode expor versão do banco, status de disco e dependências internas.

## Evidência
```
// SecurityConfig.java:52
.requestMatchers("/actuator/health/**").permitAll()
// application.yml: management.endpoint.health.show-details não encontrado no código
```

## Referências
- CWE-209: Information Exposure Through an Error Message
- OWASP A05:2021 – Security Misconfiguration
- CVSS: 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N)

## Impacto
Divulgação de informações de infraestrutura interna.

## Correção sugerida
```yaml
# application.yml
management:
  endpoint:
    health:
      show-details: never
      show-components: never
```
Considerar também restringir para apenas `/actuator/health` (sem `/**`).

## Critérios de aceite
- [ ] `GET /actuator/health` retorna apenas `{"status":"UP"}` sem subitens
- [ ] `GET /actuator/health/db` retorna 404 ou 403
""",
        },
        {
            "n": 3,
            "sec": "SEC-003",
            "titulo": "[Segurança] Grace period de refresh token permite sessão duplicada (SEC-003)",
            "labels": "security, severity:medium, session",
            "corpo": """## Descrição
`SessionService.java:62-72` aceita um refresh token já marcado como `usado` dentro da janela `reuseGrace`. Se um atacante obtiver o cookie antes do dono usá-lo novamente, pode criar uma sessão paralela sem acionar revogação.

## Por que é explorável
O mecanismo anti-replay só revoga a sessão quando o token é reutilizado **após** o grace period. Dentro da janela, duas sessões paralelas operam silenciosamente.

## Evidência
```java
// SessionService.java:65-71
if (token.getUsadoEm() != null) {
    if (token.getUsadoEm().isBefore(now.minus(reuseGrace))) {
        sessao.revogar(now); // só revoga APÓS o grace period
    }
    // dentro do grace period: nenhuma ação, sessão duplicada ativa
} else {
    token.marcarUsado(now);
}
```

## Referências
- CWE-613: Insufficient Session Expiration
- OWASP A07:2021 – Identification and Authentication Failures
- CVSS: 5.9 (AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:L/A:N)

## Impacto
Sessão duplicada sem notificação ao usuário.

## Correção sugerida
- Avaliar se o grace period é necessário; se sim, reduzir ao mínimo (2-5s)
- Registrar no AuditService sempre que um token for reutilizado dentro do grace period
- Documentar como risco aceito em `docs/adr/`

## Critérios de aceite
- [ ] Valor de `app.refresh.reuse-grace-seconds` documentado em ADR
- [ ] Evento de auditoria gerado quando token é reutilizado dentro do grace period
- [ ] OU grace period reduzido a ≤ 5 segundos
""",
        },
        {
            "n": 4,
            "sec": "SEC-004 / SEC-005 / SEC-007 / SEC-008",
            "titulo": "[Segurança] Melhorias de configuração e hardening agrupadas (SEC-004, SEC-005, SEC-007, SEC-008)",
            "labels": "security, severity:low, config, hardening",
            "corpo": """## Descrição
Grupo de melhorias de baixo risco identificadas na auditoria estática:

### SEC-004 — X-Forwarded-For sem lista de proxies confiáveis
`FORWARD_HEADERS_STRATEGY=native` em `docker-compose.yml` sem configurar `server.tomcat.remoteip.internal-proxies`. Se o backend for acessível diretamente, o IP pode ser forjado, contornando o throttle por IP.

**Correção:** Configurar `server.tomcat.remoteip.internal-proxies` com o range IP do nginx/load balancer.

### SEC-005 — Validação de segredo de exemplo incompleta
`AppProperties.java` valida `troque` apenas em `jwt.secret`. `MFA_ENCRYPTION_KEY` não tem validação equivalente.

**Correção:**
```java
// AppProperties.java — record Mfa
public Mfa {
    if (encryptionKey != null && encryptionKey.toLowerCase().contains("troque")) {
        throw new IllegalArgumentException("app.mfa.encryption-key não pode conter marcadores de exemplo");
    }
}
```

### SEC-007 — Rate limiting nginx ausente em endpoints de negócio
`locations.conf` aplica `limit_req` apenas em `/api/v1/auth/`. Endpoints como `/api/v1/notas` não têm limite nginx.

**Correção:** Adicionar zona de rate limit para `/api/v1/` com burst adequado.

### SEC-008 — Versão Spring Boot 3.5.16 inexistente no Maven Central
`pom.xml:8` declara versão que não existe publicamente. Build pode falhar em CI sem cache.

**Correção:** Atualizar para versão estável real do Spring Boot.

## Referências
- CWE-348 (SEC-004), CWE-798 (SEC-005), CWE-770 (SEC-007), CWE-1104 (SEC-008)
- OWASP A05:2021, A02:2021, API4:2023, A06:2021

## Critérios de aceite
- [ ] SEC-004: Backend não acessível diretamente em produção OU `internal-proxies` configurado
- [ ] SEC-005: Startup falha com mensagem clara se `MFA_ENCRYPTION_KEY` contém 'troque'
- [ ] SEC-007: POST `/api/v1/notas` retorna 429 após exceder limite configurado
- [ ] SEC-008: Build CI sem cache completa com sucesso
""",
        },
    ]

    for iss in issues:
        story.append(Paragraph(f"Issue #{iss['n']} — {iss['sec']}", ST["h2"]))
        story.append(Paragraph(f"<b>Título:</b> {iss['titulo']}", ST["body_left"]))
        story.append(Paragraph(f"<b>Labels:</b> {iss['labels']}", ST["small"]))
        story.append(Spacer(1, 0.15*cm))
        # Renderiza corpo como bloco de código
        story.append(code_block(iss["corpo"]))
        story.append(Spacer(1, 0.5*cm))
        story.append(hr())
        story.append(Spacer(1, 0.3*cm))

    return story

# ── Monta o documento ─────────────────────────────────────────────────────
def build_doc():
    w, h = A4
    margin_top    = 2.2*cm
    margin_bottom = 1.8*cm
    margin_left   = 1.8*cm
    margin_right  = 1.8*cm
    header_h      = 1.1*cm
    footer_h      = 0.9*cm

    dc = DocCanvas(f"Auditoria de Segurança — {PROJETO}")

    cover_frame = Frame(0, 0, w, h, leftPadding=2*cm, rightPadding=2*cm,
                        topPadding=0, bottomPadding=0, id="cover")
    normal_frame = Frame(
        margin_left, margin_bottom + footer_h,
        w - margin_left - margin_right,
        h - margin_top - margin_bottom - header_h - footer_h,
        id="normal"
    )

    doc = BaseDocTemplate(
        str(OUT),
        pagesize=A4,
        title=f"Relatório de Auditoria de Segurança — {PROJETO}",
        author=AUTOR,
        subject="Segurança de Aplicação",
        creator="Kiro AppSec",
    )
    doc.addPageTemplates([
        PageTemplate(id="cover",  frames=[cover_frame],
                     onPage=dc.on_cover),
        PageTemplate(id="normal", frames=[normal_frame],
                     onPage=dc.on_page),
    ])

    story = []
    story += build_cover()
    story += build_toc()
    story += build_exec()
    story += build_scope()
    story += build_strengths()
    story += build_findings()
    story += build_recs()
    story += build_retest()
    story += build_annexes()
    story += build_issues()

    doc.build(story)
    print(f"PDF gerado: {OUT}")
    print(f"Tamanho: {OUT.stat().st_size / 1024:.1f} KB")


if __name__ == "__main__":
    build_doc()
