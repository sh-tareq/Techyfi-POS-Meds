# Build script for Specialized Medicine Shop POS Interactive Preview
import json
import sqlite3

def generate_html():
    with open('interactive_preview/sample_medicines_preview.json', 'r', encoding='utf-8') as f:
        medicines_data = json.load(f)

    medicines_json_str = json.dumps(medicines_data, ensure_ascii=False)

    html = f'''<!DOCTYPE html>
<html lang="en" data-theme="dark" data-lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Techyfi POS — Specialized Medicine Shop POS & Database Architecture Preview</title>
  
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800;900&family=Hind+Siliguri:wght@400;500;600;700&family=JetBrains+Mono:wght@400;600;700&display=swap" rel="stylesheet">
  
  <!-- Three.js for 3D Interactive WebGL Studio -->
  <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>

  <style>
    :root {{
      --primary: #059669;
      --primary-light: #10B981;
      --primary-glow: rgba(16, 185, 129, 0.4);
      --primary-subtle: rgba(16, 185, 129, 0.12);
      
      --indigo: #4F46E5;
      --indigo-light: #6366F1;
      --indigo-glow: rgba(99, 102, 241, 0.4);
      --indigo-subtle: rgba(79, 70, 229, 0.12);

      --cyan: #0891B2;
      --cyan-light: #06B6D4;
      --cyan-glow: rgba(6, 182, 212, 0.35);
      --cyan-subtle: rgba(6, 182, 212, 0.12);

      --amber: #D97706;
      --amber-light: #F59E0B;
      --amber-glow: rgba(245, 158, 11, 0.35);
      --amber-subtle: rgba(245, 158, 11, 0.12);

      --rose: #DC2626;
      --rose-light: #EF4444;
      --rose-glow: rgba(239, 68, 68, 0.4);
      --rose-subtle: rgba(239, 68, 68, 0.12);

      --purple: #7C3AED;
      --purple-light: #8B5CF6;
      --purple-subtle: rgba(139, 92, 246, 0.12);

      --bg-base: #0B0F19;
      --bg-surface: #111827;
      --bg-card: #162032;
      --bg-card-hover: #1D2B44;
      --border-subtle: rgba(255, 255, 255, 0.08);
      --border-highlight: rgba(16, 185, 129, 0.35);

      --text-main: #F8FAFC;
      --text-muted: #94A3B8;
      --text-dim: #64748B;

      --radius-xs: 6px;
      --radius-sm: 8px;
      --radius-md: 14px;
      --radius-lg: 20px;
      --radius-xl: 28px;
      --radius-full: 9999px;

      --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.3);
      --shadow-md: 0 8px 24px rgba(0, 0, 0, 0.45);
      --shadow-lg: 0 16px 48px rgba(0, 0, 0, 0.65);
      --shadow-3d: 0 14px 28px -6px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.18);

      --font-latin: 'Plus Jakarta Sans', -apple-system, sans-serif;
      --font-bangla: 'Hind Siliguri', 'Noto Sans Bengali', sans-serif;
      --font-code: 'JetBrains Mono', monospace;
    }}

    [data-theme="light"] {{
      --bg-base: #F1F5F9;
      --bg-surface: #FFFFFF;
      --bg-card: #FFFFFF;
      --bg-card-hover: #F8FAFC;
      --border-subtle: rgba(0, 0, 0, 0.08);
      --border-highlight: rgba(16, 185, 129, 0.4);

      --text-main: #0F172A;
      --text-muted: #475569;
      --text-dim: #94A3B8;

      --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.05);
      --shadow-md: 0 8px 20px rgba(0, 0, 0, 0.08);
      --shadow-lg: 0 16px 36px rgba(0, 0, 0, 0.12);
      --shadow-3d: 0 10px 24px -4px rgba(0, 0, 0, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.95);
    }}

    * {{
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-tap-highlight-color: transparent;
    }}

    body {{
      font-family: var(--font-latin);
      background-color: var(--bg-base);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
      transition: background-color 0.3s ease, color 0.3s ease;
    }}

    [data-lang="bn"] body,
    [data-lang="bn"] .bn-font {{
      font-family: var(--font-bangla);
    }}

    /* Ambient Header */
    .top-header {{
      background: rgba(17, 24, 39, 0.85);
      backdrop-filter: blur(20px);
      border-bottom: 1px solid var(--border-subtle);
      padding: 14px 28px;
      position: sticky;
      top: 0;
      z-index: 100;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }}

    [data-theme="light"] .top-header {{
      background: rgba(255, 255, 255, 0.9);
      border-bottom-color: rgba(0, 0, 0, 0.08);
    }}

    .brand-section {{
      display: flex;
      align-items: center;
      gap: 14px;
    }}

    .brand-icon-sq {{
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: linear-gradient(135deg, #10B981 0%, #059669 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 6px 16px var(--primary-glow);
      position: relative;
      overflow: hidden;
      flex-shrink: 0;
    }}

    .brand-icon-sq img {{
      width: 100%;
      height: 100%;
      object-fit: cover;
    }}

    .brand-meta h1 {{
      font-size: 17px;
      font-weight: 800;
      letter-spacing: -0.4px;
      display: flex;
      align-items: center;
      gap: 8px;
    }}

    .brand-meta p {{
      font-size: 12px;
      color: var(--text-muted);
      font-weight: 500;
    }}

    .badge-pill {{
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 11px;
      font-weight: 700;
      padding: 3px 9px;
      border-radius: var(--radius-full);
      background: var(--primary-subtle);
      color: var(--primary-light);
      border: 1px solid var(--border-highlight);
    }}

    .global-controls {{
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }}

    .segmented-control {{
      display: inline-flex;
      background: rgba(0, 0, 0, 0.25);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 3px;
      gap: 3px;
    }}

    [data-theme="light"] .segmented-control {{
      background: rgba(0, 0, 0, 0.05);
    }}

    .seg-btn {{
      background: transparent;
      border: none;
      color: var(--text-muted);
      padding: 6px 12px;
      border-radius: var(--radius-sm);
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }}

    .seg-btn:hover {{
      color: var(--text-main);
    }}

    .seg-btn.active {{
      background: var(--primary);
      color: #FFFFFF;
      box-shadow: 0 2px 8px var(--primary-glow);
    }}

    .pill-btn {{
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      color: var(--text-main);
      padding: 7px 14px;
      border-radius: var(--radius-full);
      font-size: 12.5px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
    }}

    .pill-btn:hover {{
      border-color: var(--primary-light);
      transform: translateY(-1px);
    }}

    /* Navigation Bar */
    .nav-tabs-bar {{
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
      padding: 8px 28px;
      display: flex;
      gap: 8px;
      overflow-x: auto;
      scrollbar-width: none;
      position: sticky;
      top: 73px;
      z-index: 90;
    }}

    .nav-tabs-bar::-webkit-scrollbar {{
      display: none;
    }}

    .nav-tab-item {{
      background: transparent;
      border: 1px solid transparent;
      color: var(--text-muted);
      padding: 8px 16px;
      border-radius: var(--radius-md);
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
      transition: all 0.2s ease;
    }}

    .nav-tab-item:hover {{
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-main);
    }}

    .nav-tab-item.active {{
      background: var(--primary-subtle);
      border-color: var(--border-highlight);
      color: var(--primary-light);
    }}

    /* Main Workspace */
    .preview-workspace {{
      max-width: 1560px;
      width: 100%;
      margin: 0 auto;
      padding: 28px 24px 80px;
      display: flex;
      flex-direction: column;
      gap: 28px;
    }}

    /* Stat Bar */
    .stat-bar-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }}

    .stat-card {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      padding: 16px 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: var(--shadow-sm);
      transition: transform 0.2s ease, border-color 0.2s ease;
    }}

    .stat-card:hover {{
      transform: translateY(-2px);
      border-color: var(--border-highlight);
    }}

    .stat-icon-box {{
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;
      box-shadow: var(--shadow-sm);
    }}

    .stat-icon-box.green {{ background: var(--primary-subtle); color: var(--primary-light); border: 1px solid var(--border-highlight); }}
    .stat-icon-box.indigo {{ background: var(--indigo-subtle); color: var(--indigo-light); border: 1px solid var(--indigo-glow); }}
    .stat-icon-box.cyan {{ background: var(--cyan-subtle); color: var(--cyan-light); border: 1px solid var(--cyan-glow); }}
    .stat-icon-box.amber {{ background: var(--amber-subtle); color: var(--amber-light); border: 1px solid var(--amber-glow); }}
    .stat-icon-box.rose {{ background: var(--rose-subtle); color: var(--rose-light); border: 1px solid var(--rose-glow); }}

    .stat-info .stat-value {{
      font-size: 22px;
      font-weight: 800;
      letter-spacing: -0.5px;
      font-family: var(--font-code);
    }}

    .stat-info .stat-label {{
      font-size: 12px;
      color: var(--text-muted);
      font-weight: 600;
    }}

    /* Device Preview Frames */
    .device-stage {{
      display: flex;
      justify-content: center;
      align-items: flex-start;
      gap: 32px;
      width: 100%;
    }}

    /* Mobile Frame */
    .mobile-frame {{
      width: 400px;
      min-height: 844px;
      background: var(--bg-surface);
      border-radius: 40px;
      border: 8px solid #1E293B;
      box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.7), 0 0 0 1px rgba(255, 255, 255, 0.08);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      position: relative;
    }}

    [data-theme="light"] .mobile-frame {{
      border-color: #CBD5E1;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25), 0 0 0 1px rgba(0, 0, 0, 0.06);
    }}

    /* Tablet Frame */
    .tablet-frame {{
      width: 1100px;
      min-height: 720px;
      background: var(--bg-surface);
      border-radius: 32px;
      border: 10px solid #1E293B;
      box-shadow: 0 30px 70px -15px rgba(0, 0, 0, 0.75), 0 0 0 1px rgba(255, 255, 255, 0.08);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      position: relative;
    }}

    [data-theme="light"] .tablet-frame {{
      border-color: #CBD5E1;
      box-shadow: 0 30px 60px -12px rgba(0, 0, 0, 0.25);
    }}

    /* Desktop Full Showcase */
    .desktop-showcase {{
      width: 100%;
      background: var(--bg-surface);
      border-radius: var(--radius-xl);
      border: 1px solid var(--border-subtle);
      box-shadow: var(--shadow-lg);
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }}

    /* Mock Phone Top bar */
    .frame-status-bar {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 24px 6px;
      font-size: 11px;
      font-weight: 700;
      color: var(--text-dim);
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
    }}

    /* In-app Top Bar */
    .inapp-top-bar {{
      padding: 14px 20px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
    }}

    .inapp-title-group {{
      display: flex;
      align-items: center;
      gap: 12px;
    }}

    .inapp-icon-squircle {{
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: linear-gradient(135deg, #10B981 0%, #059669 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      box-shadow: 0 4px 12px var(--primary-glow);
    }}

    .inapp-title-group h2 {{
      font-size: 16px;
      font-weight: 800;
      letter-spacing: -0.3px;
    }}

    .inapp-title-group p {{
      font-size: 11px;
      color: var(--text-muted);
    }}

    .inapp-actions {{
      display: flex;
      align-items: center;
      gap: 8px;
    }}

    .icon-action-btn {{
      width: 38px;
      height: 38px;
      border-radius: var(--radius-md);
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      color: var(--text-main);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 16px;
      transition: all 0.2s ease;
    }}

    .icon-action-btn:hover {{
      border-color: var(--primary-light);
      color: var(--primary-light);
    }}

    /* Search & Filter Strip */
    .catalog-search-strip {{
      padding: 16px 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
    }}

    .search-input-box {{
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
    }}

    .search-input-box input {{
      width: 100%;
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 12px 16px 12px 42px;
      font-size: 13.5px;
      color: var(--text-main);
      outline: none;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }}

    .search-input-box input:focus {{
      border-color: var(--primary-light);
      box-shadow: 0 0 0 3px var(--primary-subtle);
    }}

    .search-input-box .search-lens {{
      position: absolute;
      left: 14px;
      font-size: 16px;
      color: var(--text-dim);
      pointer-events: none;
    }}

    .search-input-box .barcode-scan-btn {{
      position: absolute;
      right: 10px;
      background: var(--primary-subtle);
      border: 1px solid var(--border-highlight);
      color: var(--primary-light);
      padding: 5px 10px;
      border-radius: var(--radius-sm);
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 4px;
    }}

    .filter-chips-row {{
      display: flex;
      gap: 8px;
      overflow-x: auto;
      scrollbar-width: none;
      padding-bottom: 4px;
    }}

    .filter-chips-row::-webkit-scrollbar {{
      display: none;
    }}

    .filter-chip {{
      padding: 6px 14px;
      border-radius: var(--radius-full);
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      color: var(--text-muted);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
    }}

    .filter-chip:hover {{
      color: var(--text-main);
      border-color: var(--border-highlight);
    }}

    .filter-chip.active {{
      background: var(--primary-subtle);
      border-color: var(--border-highlight);
      color: var(--primary-light);
      font-weight: 700;
    }}

    .filter-chip.antibiotic-chip.active {{
      background: var(--rose-subtle);
      border-color: var(--rose-light);
      color: var(--rose-light);
    }}

    /* Medicine Items Grid/List */
    .medicine-catalog-scroll {{
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 640px;
    }}

    /* Medicine Card */
    .med-card {{
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      box-shadow: var(--shadow-sm);
      transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
      position: relative;
    }}

    .med-card:hover {{
      border-color: var(--border-highlight);
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
    }}

    .med-card.is-antibiotic {{
      border-left: 4px solid var(--rose-light);
    }}

    .med-header-row {{
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 10px;
    }}

    .med-identity {{
      display: flex;
      flex-direction: column;
      gap: 4px;
    }}

    .med-brand-title {{
      font-size: 15.5px;
      font-weight: 800;
      letter-spacing: -0.3px;
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }}

    .dosage-form-badge {{
      font-size: 10.5px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: var(--radius-sm);
      background: var(--indigo-subtle);
      color: var(--indigo-light);
      border: 1px solid var(--indigo-glow);
    }}

    .strength-badge {{
      font-size: 11px;
      font-weight: 700;
      color: var(--text-muted);
      font-family: var(--font-code);
    }}

    .med-generic-tag {{
      font-size: 12.5px;
      color: var(--cyan-light);
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 4px;
    }}

    .med-company-tag {{
      font-size: 11px;
      color: var(--text-dim);
      font-weight: 500;
    }}

    .med-badges-row {{
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }}

    .pill-info-badge {{
      font-size: 10px;
      font-weight: 700;
      padding: 2px 7px;
      border-radius: var(--radius-full);
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }}

    .pill-info-badge.shelf {{
      background: var(--amber-subtle);
      color: var(--amber-light);
      border: 1px solid var(--amber-glow);
    }}

    .pill-info-badge.batch {{
      background: rgba(255, 255, 255, 0.05);
      color: var(--text-muted);
      border: 1px solid var(--border-subtle);
      font-family: var(--font-code);
    }}

    .pill-info-badge.safe {{
      background: var(--primary-subtle);
      color: var(--primary-light);
      border: 1px solid var(--primary-glow);
    }}

    .pill-info-badge.near-exp {{
      background: var(--amber-subtle);
      color: var(--amber-light);
      border: 1px solid var(--amber-glow);
    }}

    .pill-info-badge.expired {{
      background: var(--rose-subtle);
      color: var(--rose-light);
      border: 1px solid var(--rose-glow);
    }}

    .pill-info-badge.rx {{
      background: var(--rose-subtle);
      color: var(--rose-light);
      border: 1px solid var(--rose-glow);
      font-weight: 800;
    }}

    /* Multi-Unit Pricing Grid */
    .med-pricing-grid {{
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      gap: 8px;
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 10px 12px;
    }}

    [data-theme="light"] .med-pricing-grid {{
      background: rgba(0, 0, 0, 0.03);
    }}

    .price-col {{
      display: flex;
      flex-direction: column;
      gap: 2px;
    }}

    .price-col .price-label {{
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      color: var(--text-dim);
      letter-spacing: 0.4px;
    }}

    .price-col .price-val {{
      font-size: 14px;
      font-weight: 800;
      color: var(--text-main);
      font-family: var(--font-code);
    }}

    .price-col .price-val.highlight {{
      color: var(--primary-light);
    }}

    .price-col .price-sub {{
      font-size: 9.5px;
      color: var(--text-dim);
    }}

    /* Card Action Strip */
    .med-card-actions {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      padding-top: 4px;
      border-top: 1px solid var(--border-subtle);
    }}

    .aux-actions {{
      display: flex;
      gap: 6px;
    }}

    .btn-aux {{
      background: transparent;
      border: 1px solid var(--border-subtle);
      color: var(--text-muted);
      padding: 6px 10px;
      border-radius: var(--radius-sm);
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      transition: all 0.2s ease;
    }}

    .btn-aux:hover {{
      border-color: var(--cyan-light);
      color: var(--cyan-light);
    }}

    .btn-add-cart {{
      background: var(--primary);
      border: none;
      color: #FFFFFF;
      padding: 7px 14px;
      border-radius: var(--radius-md);
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      box-shadow: 0 4px 12px var(--primary-glow);
      transition: all 0.2s ease;
    }}

    .btn-add-cart:hover {{
      background: var(--primary-light);
      transform: translateY(-1px);
    }}

    /* Split Terminal Layout (Tablet) */
    .split-terminal-container {{
      display: grid;
      grid-template-columns: 58% 42%;
      width: 100%;
      height: 100%;
      min-height: 720px;
    }}

    .terminal-catalog-pane {{
      border-right: 1px solid var(--border-subtle);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      background: var(--bg-surface);
    }}

    .terminal-cart-pane {{
      display: flex;
      flex-direction: column;
      background: var(--bg-card);
      overflow: hidden;
    }}

    .cart-pane-header {{
      padding: 16px 20px;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-subtle);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }}

    .cart-items-scroll {{
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }}

    .cart-item-card {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }}

    .cart-item-top {{
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }}

    .cart-item-meta h4 {{
      font-size: 13.5px;
      font-weight: 700;
    }}

    .cart-item-meta p {{
      font-size: 11px;
      color: var(--cyan-light);
    }}

    .cart-item-controls {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 10px;
    }}

    .unit-selector-pill {{
      display: inline-flex;
      background: rgba(0, 0, 0, 0.25);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-sm);
      padding: 2px;
      gap: 2px;
    }}

    .unit-pill-btn {{
      background: transparent;
      border: none;
      color: var(--text-muted);
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
    }}

    .unit-pill-btn.active {{
      background: var(--primary);
      color: #fff;
    }}

    .qty-stepper {{
      display: flex;
      align-items: center;
      gap: 6px;
    }}

    .step-btn {{
      width: 28px;
      height: 28px;
      border-radius: var(--radius-sm);
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      color: var(--text-main);
      font-size: 14px;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
    }}

    .qty-display {{
      font-size: 13px;
      font-weight: 700;
      font-family: var(--font-code);
      min-width: 24px;
      text-align: center;
    }}

    .cart-item-total {{
      font-size: 14px;
      font-weight: 800;
      font-family: var(--font-code);
      color: var(--primary-light);
    }}

    /* Dosage Instruction Picker in Cart */
    .dosage-instruction-box {{
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
      background: rgba(0, 0, 0, 0.15);
      padding: 6px 10px;
      border-radius: var(--radius-sm);
      border: 1px dashed var(--border-subtle);
    }}

    .dosage-label {{
      font-size: 10px;
      color: var(--text-dim);
      font-weight: 700;
      text-transform: uppercase;
    }}

    .dosage-chip {{
      font-size: 10.5px;
      padding: 2px 7px;
      border-radius: var(--radius-full);
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      color: var(--text-muted);
      cursor: pointer;
      transition: all 0.2s ease;
    }}

    .dosage-chip:hover {{
      border-color: var(--indigo-light);
      color: var(--text-main);
    }}

    .dosage-chip.selected {{
      background: var(--indigo-subtle);
      border-color: var(--indigo-light);
      color: var(--indigo-light);
      font-weight: 700;
    }}

    /* Antibiotic Prescription Alert Banner */
    .antibiotic-cart-alert {{
      background: linear-gradient(135deg, rgba(220, 38, 38, 0.15) 0%, rgba(185, 28, 28, 0.25) 100%);
      border: 1px solid var(--rose-light);
      border-radius: var(--radius-md);
      padding: 12px 14px;
      margin: 12px 16px 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }}

    .alert-header {{
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      font-weight: 800;
      color: var(--rose-light);
    }}

    .doctor-input-row {{
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
    }}

    .doctor-input-row input {{
      width: 100%;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: var(--radius-sm);
      padding: 6px 10px;
      font-size: 11px;
      color: #fff;
      outline: none;
    }}

    /* Cart Summary & Checkout Footer */
    .cart-summary-footer {{
      padding: 16px 20px;
      background: var(--bg-surface);
      border-top: 1px solid var(--border-subtle);
      display: flex;
      flex-direction: column;
      gap: 12px;
    }}

    .summary-calc-row {{
      display: flex;
      justify-content: space-between;
      font-size: 12.5px;
      color: var(--text-muted);
    }}

    .summary-calc-row.grand-total {{
      font-size: 17px;
      font-weight: 800;
      color: var(--text-main);
      padding-top: 8px;
      border-top: 1px dashed var(--border-subtle);
    }}

    .summary-calc-row.grand-total .amt {{
      color: var(--primary-light);
      font-family: var(--font-code);
    }}

    .btn-checkout-print {{
      width: 100%;
      background: linear-gradient(135deg, #10B981 0%, #059669 100%);
      border: none;
      color: #FFFFFF;
      padding: 14px;
      border-radius: var(--radius-md);
      font-size: 14px;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      box-shadow: 0 6px 20px var(--primary-glow);
      transition: all 0.2s ease;
    }}

    .btn-checkout-print:hover {{
      transform: translateY(-2px);
      box-shadow: 0 8px 25px var(--primary-glow);
    }}

    /* 3D WebGL Studio Section */
    .webgl-studio-container {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-xl);
      padding: 24px;
      display: grid;
      grid-template-columns: 1fr 340px;
      gap: 24px;
      box-shadow: var(--shadow-lg);
    }}

    @media (max-width: 960px) {{
      .webgl-studio-container {{
        grid-template-columns: 1fr;
      }}
    }}

    #canvas-container-3d {{
      width: 100%;
      height: 480px;
      border-radius: var(--radius-lg);
      background: radial-gradient(circle at 50% 50%, #162032 0%, #0B0F19 100%);
      position: relative;
      overflow: hidden;
      border: 1px solid var(--border-subtle);
    }}

    .canvas-overlay-hints {{
      position: absolute;
      bottom: 16px;
      left: 16px;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(8px);
      padding: 8px 14px;
      border-radius: var(--radius-full);
      font-size: 11px;
      color: var(--text-muted);
      border: 1px solid var(--border-subtle);
      pointer-events: none;
    }}

    .studio-controls-pane {{
      display: flex;
      flex-direction: column;
      gap: 18px;
    }}

    .control-card {{
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }}

    .control-card h4 {{
      font-size: 12.5px;
      font-weight: 700;
      color: var(--text-main);
      display: flex;
      align-items: center;
      gap: 6px;
    }}

    /* Thermal Receipt Preview Paper */
    .receipt-container-wrap {{
      display: flex;
      justify-content: center;
      padding: 24px 0;
    }}

    .thermal-paper-slip {{
      width: 380px;
      background: #FFFFFF;
      color: #111827;
      padding: 24px 20px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 11.5px;
      line-height: 1.45;
      box-shadow: 0 15px 40px rgba(0, 0, 0, 0.4);
      position: relative;
      border-radius: 4px;
    }}

    .thermal-paper-slip::after {{
      content: '';
      position: absolute;
      bottom: -10px;
      left: 0;
      width: 100%;
      height: 10px;
      background: radial-gradient(circle, transparent, transparent 50%, #FFFFFF 50%, #FFFFFF 100%);
      background-size: 16px 16px;
    }}

    .receipt-header {{
      text-align: center;
      border-bottom: 1px dashed #4B5563;
      padding-bottom: 12px;
      margin-bottom: 12px;
    }}

    .receipt-header h2 {{
      font-size: 15px;
      font-weight: 800;
      margin-bottom: 4px;
    }}

    .receipt-header p {{
      font-size: 10.5px;
      color: #4B5563;
    }}

    .receipt-info-row {{
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
      font-size: 11px;
    }}

    .receipt-items-table {{
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0;
      border-bottom: 1px dashed #4B5563;
      padding-bottom: 10px;
    }}

    .receipt-items-table th {{
      text-align: left;
      font-size: 10px;
      padding-bottom: 6px;
      border-bottom: 1px solid #9CA3AF;
    }}

    .receipt-items-table td {{
      padding: 6px 0;
      font-size: 11px;
      vertical-align: top;
    }}

    .receipt-footer-calc {{
      display: flex;
      flex-direction: column;
      gap: 4px;
      border-bottom: 1px dashed #4B5563;
      padding-bottom: 10px;
      margin-bottom: 12px;
    }}

    .receipt-calc-row {{
      display: flex;
      justify-content: space-between;
    }}

    .receipt-calc-row.bold {{
      font-weight: 800;
      font-size: 13px;
    }}

    .receipt-disclaimer {{
      text-align: center;
      font-size: 9.5px;
      color: #4B5563;
      margin-top: 8px;
    }}

    /* Generic Alternative Modal & Drawer */
    .substitute-modal-backdrop {{
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      z-index: 200;
      display: none;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }}

    .substitute-modal-box {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-xl);
      max-width: 680px;
      width: 100%;
      max-height: 85vh;
      overflow-y: auto;
      box-shadow: var(--shadow-lg);
      display: flex;
      flex-direction: column;
    }}

    .modal-head {{
      padding: 20px;
      border-bottom: 1px solid var(--border-subtle);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }}

    .modal-body {{
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }}

    /* Photorealistic 3D Squircle Showcase Cards */
    .icon-gallery-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
    }}

    .icon-showcase-card {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 18px;
      box-shadow: var(--shadow-md);
      transition: transform 0.3s ease, border-color 0.3s ease;
    }}

    .icon-showcase-card:hover {{
      transform: translateY(-3px);
      border-color: var(--border-highlight);
    }}

    .showcase-sq-img {{
      width: 72px;
      height: 72px;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.5);
      flex-shrink: 0;
    }}

    .showcase-sq-img img {{
      width: 100%;
      height: 100%;
      object-fit: cover;
    }}

    .showcase-details h4 {{
      font-size: 15px;
      font-weight: 800;
      margin-bottom: 4px;
    }}

    .showcase-details p {{
      font-size: 12px;
      color: var(--text-muted);
      line-height: 1.45;
    }}

    /* Architecture Blueprint Table */
    .arch-table-wrap {{
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      overflow-x: auto;
      box-shadow: var(--shadow-md);
    }}

    .arch-table {{
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }}

    .arch-table th {{
      background: rgba(0, 0, 0, 0.25);
      padding: 14px 18px;
      font-size: 12px;
      font-weight: 700;
      color: var(--text-dim);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border-bottom: 1px solid var(--border-subtle);
    }}

    .arch-table td {{
      padding: 14px 18px;
      font-size: 13px;
      border-bottom: 1px solid var(--border-subtle);
      vertical-align: top;
    }}

    .arch-table tr:hover td {{
      background: rgba(255, 255, 255, 0.02);
    }}

    .pill-diff-old {{
      background: var(--rose-subtle);
      color: var(--rose-light);
      padding: 2px 8px;
      border-radius: var(--radius-full);
      font-size: 11px;
      font-weight: 700;
    }}

    .pill-diff-new {{
      background: var(--primary-subtle);
      color: var(--primary-light);
      padding: 2px 8px;
      border-radius: var(--radius-full);
      font-size: 11px;
      font-weight: 700;
    }}
  </style>
</head>
<body>

  <!-- Top Global Header -->
  <header class="top-header">
    <div class="brand-section">
      <div class="brand-icon-sq">
        <img src="assets/3d_medicine_catalog.jpg" alt="Medicine POS" onerror="this.src=''; this.innerHTML='💊';">
      </div>
      <div class="brand-meta">
        <h1>Techyfi Pharma POS <span class="badge-pill">DIMS v2026.10</span></h1>
        <p class="lang-text" data-en="Specialized Pharmacy & Medicine Database Architecture" data-bn="বিশেষায়িত ফার্মেসি ও ওষুধ ডেটাবেজ আর্কিটেকচার">Specialized Pharmacy & Medicine Database Architecture</p>
      </div>
    </div>

    <div class="global-controls">
      <!-- Device View -->
      <div class="segmented-control" id="device-selector">
        <button class="seg-btn" onclick="setDeviceView('phone')">📱 <span class="lang-text" data-en="Phone" data-bn="ফোন">Phone</span></button>
        <button class="seg-btn active" onclick="setDeviceView('tablet')">💻 <span class="lang-text" data-en="Tablet Split" data-bn="ট্যাবলেট টার্মিনাল">Tablet Split</span></button>
        <button class="seg-btn" onclick="setDeviceView('desktop')">🖥️ <span class="lang-text" data-en="Full Catalog" data-bn="ফুল স্ক্রিন">Full Catalog</span></button>
      </div>

      <!-- Language Switcher -->
      <div class="segmented-control" id="lang-selector">
        <button class="seg-btn active" onclick="setAppLanguage('en')">🇬🇧 EN</button>
        <button class="seg-btn" onclick="setAppLanguage('bn')">🇧🇩 বাং</button>
      </div>

      <!-- Theme Switcher -->
      <button class="pill-btn" onclick="toggleTheme()">
        <span id="theme-icon">🌙</span> <span id="theme-text" class="lang-text" data-en="Dark" data-bn="ডার্ক">Dark</span>
      </button>
    </div>
  </header>

  <!-- Navigation Bar -->
  <nav class="nav-tabs-bar">
    <button class="nav-tab-item active" onclick="switchMainTab('catalog')">
      💊 <span class="lang-text" data-en="Medicine Catalog (Replacing Products)" data-bn="ওষুধ ক্যাটালগ (প্রোডাক্টসের পরিবর্তে)">Medicine Catalog (Replacing Products)</span>
    </button>
    <button class="nav-tab-item" onclick="switchMainTab('terminal')">
      🛒 <span class="lang-text" data-en="Pharmacy Sell & Cart Terminal" data-bn="ফার্মেসি বিক্রয় ও কার্ট টার্মিনাল">Pharmacy Sell & Cart Terminal</span>
    </button>
    <button class="nav-tab-item" onclick="switchMainTab('substitutes')">
      🔄 <span class="lang-text" data-en="Generic Substitute Finder" data-bn="বিকল্প ব্র্যান্ড ও জেনেরিক সন্ধান">Generic Substitute Finder</span>
    </button>
    <button class="nav-tab-item" onclick="switchMainTab('receipt')">
      🧾 <span class="lang-text" data-en="Specialized Thermal Receipt (58/80mm)" data-bn="ফার্মেসি থার্মাল রসিদ (৫৮/৮০ মিমি)">Specialized Thermal Receipt (58/80mm)</span>
    </button>
    <button class="nav-tab-item" onclick="switchMainTab('studio3d')">
      🧊 <span class="lang-text" data-en="3D WebGL Studio & Icons" data-bn="৩ডি ওয়েবজিএল স্টুডিও ও আইকন">3D WebGL Studio & Icons</span>
    </button>
    <button class="nav-tab-item" onclick="switchMainTab('blueprint')">
      🏛️ <span class="lang-text" data-en="Architecture & Improvement Plan" data-bn="আর্কিটেকচার ও প্রস্তাবিত আপগ্রেড">Architecture & Improvement Plan</span>
    </button>
  </nav>

  <!-- Main Preview Workspace -->
  <main class="preview-workspace">

    <!-- KPI Metric Stat Bar -->
    <div class="stat-bar-grid">
      <div class="stat-card">
        <div class="stat-icon-box green">💊</div>
        <div class="stat-info">
          <div class="stat-value">29,834</div>
          <div class="stat-label lang-text" data-en="Total DIMS Medicines" data-bn="মোট নিবন্ধিত ওষুধ">Total DIMS Medicines</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box cyan">🧬</div>
        <div class="stat-info">
          <div class="stat-value">1,425</div>
          <div class="stat-label lang-text" data-en="Clinical Generics" data-bn="ক্লিনিক্যাল জেনেরিক">Clinical Generics</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box indigo">🏭</div>
        <div class="stat-info">
          <div class="stat-value">692</div>
          <div class="stat-label lang-text" data-en="Pharma Manufacturers" data-bn="ওষুধ প্রস্তুতকারক কোম্পানি">Pharma Manufacturers</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box rose">⚠️</div>
        <div class="stat-info">
          <div class="stat-value">4,443</div>
          <div class="stat-label lang-text" data-en="Antibiotics (Rx Guard)" data-bn="অ্যান্টিবায়োটিক (প্রেসক্রিপশন)">Antibiotics (Rx Guard)</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-box amber">📍</div>
        <div class="stat-info">
          <div class="stat-value">FEFO</div>
          <div class="stat-label lang-text" data-en="Batch & Expiry Safe" data-bn="ব্যাচ ও মেয়াদোত্তীর্ণ ট্র্যাকিং">Batch & Expiry Safe</div>
        </div>
      </div>
    </div>

    <!-- Active Screen View Container -->
    <div id="screen-catalog-view" class="screen-view active">
      <div class="device-stage">
        
        <!-- Mobile Frame View -->
        <div class="mobile-frame" id="phone-catalog-container" style="display: none;">
          <div class="frame-status-bar">
            <span>09:41</span>
            <span>5G 100%</span>
          </div>

          <div class="inapp-top-bar">
            <div class="inapp-title-group">
              <div class="inapp-icon-squircle">💊</div>
              <div>
                <h2 class="lang-text" data-en="Medicines" data-bn="ওষুধ তালিকা">Medicines</h2>
                <p class="lang-text" data-en="DIMS Specialized Pharmacy DB" data-bn="বিশেষায়িত ফার্মেসি ডেটাবেজ">DIMS Specialized Pharmacy DB</p>
              </div>
            </div>
            <div class="inapp-actions">
              <button class="icon-action-btn" onclick="openAddMedicineModal()">➕</button>
            </div>
          </div>

          <div class="catalog-search-strip">
            <div class="search-input-box">
              <span class="search-lens">🔍</span>
              <input type="text" id="phone-search-input" placeholder="Search Brand, Generic, or Company..." oninput="handleSearch(this.value)">
              <button class="barcode-scan-btn">📷 Scan</button>
            </div>
            <div class="filter-chips-row">
              <button class="filter-chip active" onclick="filterByForm('all')">All</button>
              <button class="filter-chip" onclick="filterByForm('Tablet')">Tablet</button>
              <button class="filter-chip" onclick="filterByForm('Capsule')">Capsule</button>
              <button class="filter-chip" onclick="filterByForm('Syrup')">Syrup</button>
              <button class="filter-chip antibiotic-chip" onclick="filterByForm('antibiotic')">🚨 Antibiotic (Rx)</button>
            </div>
          </div>

          <div class="medicine-catalog-scroll" id="phone-meds-list">
            <!-- Dynamic Content Injected by JS -->
          </div>
        </div>

        <!-- Tablet Split Terminal View (Default) -->
        <div class="tablet-frame" id="tablet-catalog-container">
          <div class="frame-status-bar">
            <span>Dhaka • Specialized Pharmacy POS Terminal #01</span>
            <span>Online • Cashier: Tanvir • 09:41 AM</span>
          </div>

          <div class="split-terminal-container">
            <!-- Left Side: Medicine Catalog (60%) -->
            <div class="terminal-catalog-pane">
              <div class="inapp-top-bar">
                <div class="inapp-title-group">
                  <div class="inapp-icon-squircle">💊</div>
                  <div>
                    <h2 class="lang-text" data-en="Medicine Catalog & Stock" data-bn="ওষুধ ক্যাটালগ ও স্টক">Medicine Catalog & Stock</h2>
                    <p class="lang-text" data-en="Instant Generic Search & Alternative Substitutes" data-bn="দ্রুত জেনেরিক অনুসন্ধান ও বিকল্প ওষুধ সন্ধান">Instant Generic Search & Alternative Substitutes</p>
                  </div>
                </div>
                <div class="inapp-actions">
                  <button class="pill-btn" onclick="openAddMedicineModal()">➕ <span class="lang-text" data-en="Add Medicine" data-bn="নতুন ওষুধ">Add Medicine</span></button>
                </div>
              </div>

              <div class="catalog-search-strip">
                <div class="search-input-box">
                  <span class="search-lens">🔍</span>
                  <input type="text" id="tablet-search-input" placeholder="Search Brand (Napa), Generic (Paracetamol), Company (Square)..." oninput="handleSearch(this.value)">
                  <button class="barcode-scan-btn">📷 Hardware Barcode Ready</button>
                </div>
                <div class="filter-chips-row">
                  <button class="filter-chip active" onclick="filterByForm('all')">All Forms (সব ধরন)</button>
                  <button class="filter-chip" onclick="filterByForm('Tablet')">Tablet (ট্যাবলেট)</button>
                  <button class="filter-chip" onclick="filterByForm('Capsule')">Capsule (ক্যাপসুল)</button>
                  <button class="filter-chip" onclick="filterByForm('Syrup')">Syrup (সিরাপ)</button>
                  <button class="filter-chip" onclick="filterByForm('Injection')">Injection (ইনজেকশন)</button>
                  <button class="filter-chip" onclick="filterByForm('Eye Drops')">Eye Drops (চোখের ড্রপ)</button>
                  <button class="filter-chip antibiotic-chip" onclick="filterByForm('antibiotic')">🚨 Antibiotic Rx (প্রেসক্রিপশন)</button>
                  <button class="filter-chip" onclick="filterByForm('near_expiry')">⏳ Near Expiry (মেয়াদ সতর্কবার্তা)</button>
                </div>
              </div>

              <div class="medicine-catalog-scroll" id="tablet-meds-list">
                <!-- Dynamic Content Injected by JS -->
              </div>
            </div>

            <!-- Right Side: Live Cart & Bill Staging (40%) -->
            <div class="terminal-cart-pane">
              <div class="cart-pane-header">
                <div>
                  <h3 class="lang-text" data-en="Patient Cart & Billing" data-bn="রোগীর বিলিং কার্ট">Patient Cart & Billing</h3>
                  <p style="font-size: 11px; color: var(--text-dim);" class="lang-text" data-en="Multi-Unit Pcs / Strip / Box Selling" data-bn="পিস / পাতা / বক্স বহু-একক বিক্রয়">Multi-Unit Pcs / Strip / Box Selling</p>
                </div>
                <button class="btn-aux" onclick="clearCart()" style="color: var(--rose-light);">🗑️ <span class="lang-text" data-en="Clear" data-bn="খালি করুন">Clear</span></button>
              </div>

              <!-- Antibiotic Alert Banner (Appears if any antibiotic in cart) -->
              <div class="antibiotic-cart-alert" id="cart-antibiotic-banner" style="display: none;">
                <div class="alert-header">
                  <span>🚨</span>
                  <span class="lang-text" data-en="Antibiotic Rx Verification Required" data-bn="অ্যান্টিবায়োটিক প্রেসক্রিপশন যাচাইকরণ আবশ্যক">Antibiotic Rx Verification Required</span>
                </div>
                <p style="font-size: 10.5px; color: #FECACA;" class="lang-text" data-en="Prescription mandatory by DGDA. Record prescribing doctor details:" data-bn="ডিজিডিএ বিধি অনুযায়ী প্রেসক্রিপশন বাধ্যতামূলক। ডাক্তারের তথ্য রেকর্ড করুন:">Prescription mandatory by DGDA. Record prescribing doctor details:</p>
                <div class="doctor-input-row">
                  <input type="text" id="doc-name-input" placeholder="Doctor Name (e.g. Dr. Tanvir)">
                  <input type="text" id="doc-bmdc-input" placeholder="BMDC Reg # (e.g. A-84920)">
                </div>
              </div>

              <!-- Cart Line Items -->
              <div class="cart-items-scroll" id="cart-items-container">
                <!-- Cart items populated by JS -->
              </div>

              <!-- Cart Summary & Checkout -->
              <div class="cart-summary-footer">
                <div class="summary-calc-row">
                  <span class="lang-text" data-en="Items Count:" data-bn="আইটেম সংখ্যা:">Items Count:</span>
                  <span id="summary-items-count">0 items</span>
                </div>
                <div class="summary-calc-row">
                  <span class="lang-text" data-en="Subtotal:" data-bn="উপমোট:">Subtotal:</span>
                  <span id="summary-subtotal">৳0.00</span>
                </div>
                <div class="summary-calc-row">
                  <span class="lang-text" data-en="Prescription Discount:" data-bn="ফার্মেসি ডিসকাউন্ট:">Prescription Discount:</span>
                  <span id="summary-discount" style="color: var(--primary-light);">-৳0.00</span>
                </div>
                <div class="summary-calc-row grand-total">
                  <span class="lang-text" data-en="Net Total Payable:" data-bn="সর্বমোট প্রদেয়:">Net Total Payable:</span>
                  <span class="amt" id="summary-grand-total">৳0.00</span>
                </div>

                <button class="btn-checkout-print" onclick="proceedToReceipt()">
                  🧾 <span class="lang-text" data-en="Print Thermal Receipt (58/80mm)" data-bn="থার্মাল রসিদ প্রিন্ট করুন">Print Thermal Receipt (58/80mm)</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Full Desktop View -->
        <div class="desktop-showcase" id="desktop-catalog-container" style="display: none;">
          <div class="inapp-top-bar">
            <div class="inapp-title-group">
              <div class="inapp-icon-squircle">💊</div>
              <div>
                <h2>Enterprise Pharmacy Medicine Catalog</h2>
                <p>29,834 Medicines • FTS5 Sub-Millisecond Search • Full Multi-Unit Strip Pricing</p>
              </div>
            </div>
          </div>

          <div class="catalog-search-strip">
            <div class="search-input-box">
              <span class="search-lens">🔍</span>
              <input type="text" id="desktop-search-input" placeholder="Search Brand, Generic, or Company across 29,834 records..." oninput="handleSearch(this.value)">
            </div>
          </div>

          <div class="medicine-catalog-scroll" id="desktop-meds-list" style="max-height: 800px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <!-- Grid Mode Content -->
          </div>
        </div>

      </div>
    </div>

    <!-- Generic Substitute Finder Screen -->
    <div id="screen-substitutes-view" class="screen-view" style="display: none;">
      <div class="desktop-showcase">
        <div class="inapp-top-bar">
          <div class="inapp-title-group">
            <div class="inapp-icon-squircle" style="background: linear-gradient(135deg, #06B6D4 0%, #0891B2 100%);">🔄</div>
            <div>
              <h2 class="lang-text" data-en="Generic Substitute & Alternative Brand Engine" data-bn="বিকল্প ব্র্যান্ড ও জেনেরিক অল্টারনেটিভ ফাইন্ডার">Generic Substitute & Alternative Brand Engine</h2>
              <p class="lang-text" data-en="Never lose a sale due to out-of-stock medicine. Instant therapeutic equivalent matching." data-bn="স্টক শেষ হলেও কাস্টমার ফেরত যাবে না। মুহূর্তে একই জেনেরিকের বিকল্প ওষুধ খুঁজে নিন।">Never lose a sale due to out-of-stock medicine. Instant therapeutic equivalent matching.</p>
            </div>
          </div>
        </div>

        <div style="background: var(--bg-card); border: 1px solid var(--border-subtle); border-radius: var(--radius-lg); padding: 20px;">
          <h3 style="font-size: 15px; margin-bottom: 12px;" class="lang-text" data-en="Select a Reference Medicine to Find Substitutes:" data-bn="বিকল্প খুঁজতে যেকোনো একটি ওষুধ নির্বাচন করুন:">Select a Reference Medicine to Find Substitutes:</h3>
          <div style="display: flex; gap: 10px; flex-wrap: wrap;">
            <button class="pill-btn active" onclick="loadSubstituteDemo('Napa 500mg Tablet')">Napa 500mg Tablet (Paracetamol)</button>
            <button class="pill-btn" onclick="loadSubstituteDemo('Seclo 20mg Capsule')">Seclo 20mg Capsule (Omeprazole)</button>
            <button class="pill-btn" onclick="loadSubstituteDemo('Cef-3 200mg Capsule')">Cef-3 200mg Capsule (Cefixime)</button>
            <button class="pill-btn" onclick="loadSubstituteDemo('Monas 10mg Tablet')">Monas 10mg Tablet (Montelukast)</button>
            <button class="pill-btn" onclick="loadSubstituteDemo('Alatrol 10mg Tablet')">Alatrol 10mg Tablet (Cetirizine)</button>
          </div>
        </div>

        <div id="substitute-results-pane" style="display: flex; flex-direction: column; gap: 16px;">
          <!-- Dynamically populated alternatives -->
        </div>
      </div>
    </div>

    <!-- Thermal Receipt Preview Screen -->
    <div id="screen-receipt-view" class="screen-view" style="display: none;">
      <div class="desktop-showcase" style="align-items: center;">
        <div style="display: flex; justify-content: space-between; align-items: center; width: 100%; max-width: 600px;">
          <div>
            <h3 class="lang-text" data-en="Specialized Pharmacy Thermal Slip" data-bn="বিশেষায়িত ফার্মেসি থার্মাল রসিদ">Specialized Pharmacy Thermal Slip</h3>
            <p style="font-size: 12px; color: var(--text-muted);" class="lang-text" data-en="DGDA License, Doctor Details, Dosage & Batch Printing" data-bn="ডিজিডিএ লাইসেন্স, ডাক্তারের তথ্য, সেবনবিধি ও ব্যাচ নম্বর সহ">DGDA License, Doctor Details, Dosage & Batch Printing</p>
          </div>
          <div class="segmented-control">
            <button class="seg-btn active" onclick="setReceiptWidth('58mm')">58mm (2")</button>
            <button class="seg-btn" onclick="setReceiptWidth('80mm')">80mm (3")</button>
          </div>
        </div>

        <div class="receipt-container-wrap">
          <div class="thermal-paper-slip" id="thermal-slip">
            <div class="receipt-header">
              <h2>মেসার্স নূর ফার্মেসি ও মেডিসিন কর্নার</h2>
              <p>NOOR PHARMACY & HEALTHCARE</p>
              <p>DGDA ড্রাগ লাইসেন্স নং: <strong>DL-DHK-48291/2024</strong></p>
              <p>বাড়ি #১২, রোড #৪, ধানমন্ডি, ঢাকা • 01712-345678</p>
            </div>

            <div class="receipt-info-row">
              <span>চালান নং: #PH-2026-9042</span>
              <span>তারিখ: 24-Oct-2026 09:42 AM</span>
            </div>
            <div class="receipt-info-row">
              <span>ক্যাশিয়ার: মো. তানভীর</span>
              <span>কাউন্টার: POS-01</span>
            </div>
            <div class="receipt-info-row">
              <span>রোগীর নাম: মো. রফিকুল ইসলাম</span>
              <span>মোবাইল: 01819-234567</span>
            </div>
            <div class="receipt-info-row" style="color: #047857; font-weight: 700;">
              <span>প্রেসক্রিপশন: ডা. এ. কে. এম. তানভীর (BMDC A-84920)</span>
            </div>

            <table class="receipt-items-table">
              <thead>
                <tr>
                  <th>ওষুধের নাম ও সেবনবিধি</th>
                  <th style="text-align: center;">পরিমাণ</th>
                  <th style="text-align: right;">মূল্য</th>
                  <th style="text-align: right;">মোট</th>
                </tr>
              </thead>
              <tbody id="receipt-table-body">
                <tr>
                  <td>
                    <strong>Napa 500mg Tablet</strong><br>
                    <span style="font-size: 9.5px; color: #4B5563;">Paracetamol (Beximco)</span><br>
                    <span style="font-size: 10px; color: #047857; font-weight: bold;">[বিধি: ১+০+১ (খাওয়ার পর)]</span><br>
                    <span style="font-size: 9px; color: #6B7280;">Batch: BX2402 • Exp: 04/28</span>
                  </td>
                  <td style="text-align: center;">১ পাতা (১০টি)</td>
                  <td style="text-align: right;">৳১২.০০</td>
                  <td style="text-align: right;">৳১২.০০</td>
                </tr>
                <tr>
                  <td>
                    <strong>Seclo 20mg Capsule</strong><br>
                    <span style="font-size: 9.5px; color: #4B5563;">Omeprazole (Square)</span><br>
                    <span style="font-size: 10px; color: #047857; font-weight: bold;">[বিধি: ১+০+০ (খাওয়ার ৩০ মিনিট আগে)]</span><br>
                    <span style="font-size: 9px; color: #6B7280;">Batch: SC918 • Exp: 11/27</span>
                  </td>
                  <td style="text-align: center;">২ পাতা (২০টি)</td>
                  <td style="text-align: right;">৳৬০.০০</td>
                  <td style="text-align: right;">৳১২০.০০</td>
                </tr>
                <tr>
                  <td>
                    <strong>Cef-3 200mg Capsule 🚨</strong><br>
                    <span style="font-size: 9.5px; color: #DC2626; font-weight: bold;">অ্যান্টিবায়োটিক (Cefixime)</span><br>
                    <span style="font-size: 10px; color: #047857; font-weight: bold;">[বিধি: ১+০+১ (টানা ৭ দিন সেব্য)]</span><br>
                    <span style="font-size: 9px; color: #6B7280;">Batch: CF402 • Exp: 08/27</span>
                  </td>
                  <td style="text-align: center;">১ পাতা (১৪টি)</td>
                  <td style="text-align: right;">৳৪৯০.০০</td>
                  <td style="text-align: right;">৳৪৯০.০০</td>
                </tr>
              </tbody>
            </table>

            <div class="receipt-footer-calc">
              <div class="receipt-calc-row">
                <span>উপমোট (Subtotal):</span>
                <span>৳৬২২.০০</span>
              </div>
              <div class="receipt-calc-row">
                <span>ফার্মেসি ছাড় (Discount):</span>
                <span>-৳২২.০০</span>
              </div>
              <div class="receipt-calc-row bold">
                <span>সর্বমোট বিল (Net Total):</span>
                <span>৳৬০০.০০</span>
              </div>
              <div class="receipt-calc-row">
                <span>পরিশোধিত নগদ (Cash Paid):</span>
                <span>৳১,০০০.০০</span>
              </div>
              <div class="receipt-calc-row">
                <span>ফেরত (Change Due):</span>
                <span>৳৪০০.০০</span>
              </div>
            </div>

            <div class="receipt-disclaimer">
              <p>⚠️ অনুগ্রহ করে অ্যান্টিবায়োটিকের সম্পূর্ণ কোর্স সমাপ্ত করুন।</p>
              <p>ওষুধ আলো ও আর্দ্রতা থেকে দূরে ৩০° সে. এর নিচে রাখুন।</p>
              <p>কাটা বা নষ্ট পাতা ওষুধ ফেরত নেওয়া হয় না। সুস্থ থাকুন।</p>
              <p style="margin-top: 6px; font-weight: bold;">Techyfi POS Medicine Edition • www.techyfi.com</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3D Studio & Iconography Screen -->
    <div id="screen-studio3d-view" class="screen-view" style="display: none;">
      <div class="desktop-showcase">
        <div class="inapp-top-bar">
          <div class="inapp-title-group">
            <div class="inapp-icon-squircle" style="background: linear-gradient(135deg, #10B981 0%, #06B6D4 100%);">🧊</div>
            <div>
              <h2 class="lang-text" data-en="Interactive 3D Medicine Studio & Squircle Badges" data-bn="৩ডি ইন্টারেক্টিভ মেডিসিন স্টুডিও ও স্কুইরকল আইকন">Interactive 3D Medicine Studio & Squircle Badges</h2>
              <p class="lang-text" data-en="Live Three.js WebGL Rotatable Blister Pack & Photorealistic 3D Category Suite" data-bn="রিয়েল-টাইম থ্রিজেএস ৩ডি ওষুধ স্ট্রিপ ও ক্যাটাগরি আইকন স্যুট">Live Three.js WebGL Rotatable Blister Pack & Photorealistic 3D Category Suite</p>
            </div>
          </div>
        </div>

        <!-- WebGL Canvas Box -->
        <div class="webgl-studio-container">
          <div id="canvas-container-3d">
            <div class="canvas-overlay-hints">
              🖱️ <span class="lang-text" data-en="Click & Drag to rotate 3D blister strip • Scroll to zoom" data-bn="মাউস ড্র্যাগ করে ৩ডি ওষুধ পাতা ঘুরিয়ে দেখুন • জুম করতে স্ক্রোল করুন">Click & Drag to rotate 3D blister strip • Scroll to zoom</span>
            </div>
          </div>

          <div class="studio-controls-pane">
            <div class="control-card">
              <h4>🎨 <span class="lang-text" data-en="Capsule Color Theme" data-bn="ক্যাপসুল কালার থিম">Capsule Color Theme</span></h4>
              <div class="segmented-control" style="width: 100%;">
                <button class="seg-btn active" style="flex: 1;" onclick="set3DCapsuleColor('red-white')">Red & White</button>
                <button class="seg-btn" style="flex: 1;" onclick="set3DCapsuleColor('blue-cyan')">Cyan & Blue</button>
                <button class="seg-btn" style="flex: 1;" onclick="set3DCapsuleColor('emerald-gold')">Emerald</button>
              </div>
            </div>

            <div class="control-card">
              <h4>💡 <span class="lang-text" data-en="Studio Lighting" data-bn="স্টুডিও লাইটিং">Studio Lighting</span></h4>
              <div class="segmented-control" style="width: 100%;">
                <button class="seg-btn active" style="flex: 1;" onclick="set3DLighting('studio')">Clinical Daylight</button>
                <button class="seg-btn" style="flex: 1;" onclick="set3DLighting('neon')">Cyber Emerald</button>
              </div>
            </div>

            <div class="control-card">
              <h4>⚡ <span class="lang-text" data-en="Animation Physics" data-bn="অ্যানিমেশন ফিজিক্স">Animation Physics</span></h4>
              <button class="btn-checkout-print" style="padding: 8px 12px; font-size: 12px;" onclick="trigger3DFlip()">
                🔄 <span class="lang-text" data-en="Trigger 3D Flip Spin" data-bn="৩ডি স্পিন ঘোরান">Trigger 3D Flip Spin</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 3D Category Badges Gallery -->
        <h3 style="font-size: 16px; margin-top: 12px;" class="lang-text" data-en="Photorealistic 3D Squircle Badges (Rendered for Jetpack Compose):" data-bn="ফটো-রিয়ালিস্টিক ৩ডি স্কুইরকল ব্যাজ (জেটপ্যাক কম্পোজের জন্য প্রস্তুত):">Photorealistic 3D Squircle Badges (Rendered for Jetpack Compose):</h3>

        <div class="icon-gallery-grid">
          <div class="icon-showcase-card">
            <div class="showcase-sq-img">
              <img src="assets/3d_medicine_catalog.jpg" alt="Medicine Catalog">
            </div>
            <div class="showcase-details">
              <h4>💊 Medicines & Generics</h4>
              <p class="lang-text" data-en="Photorealistic blister pack with floating capsules on emerald glass squircle." data-bn="পান্না সবুজ স্কুইরকলে ৩ডি ফোস্কা প্যাক ও ভাসমান ক্যাপসুল।">Photorealistic blister pack with floating capsules on emerald glass squircle.</p>
            </div>
          </div>

          <div class="icon-showcase-card">
            <div class="showcase-sq-img">
              <img src="assets/3d_rx_prescription.jpg" alt="Rx Prescription">
            </div>
            <div class="showcase-details">
              <h4>🩺 Rx & Doctor Stethoscope</h4>
              <p class="lang-text" data-en="Cyan clinical clipboard with glowing stethoscope and embossed Rx symbol." data-bn="নিয়ন স্টেথোস্কোপ ও আর-এক্স প্রেসক্রিপশন ক্লিপবোর্ড।">Cyan clinical clipboard with glowing stethoscope and embossed Rx symbol.</p>
            </div>
          </div>

          <div class="icon-showcase-card">
            <div class="showcase-sq-img">
              <img src="assets/3d_pharmacy_shelf.jpg" alt="Pharmacy Shelf">
            </div>
            <div class="showcase-details">
              <h4>🏷️ Pharmacy Shelf & Drawer</h4>
              <p class="lang-text" data-en="Warm ambient illuminated pharmacy shelf with organized medicine boxes." data-bn="ফার্মেসি ওষুধ তাক ও র‍্যাক অবস্থান নির্দেশক ৩ডি ক্যাবিনেট।">Warm ambient illuminated pharmacy shelf with organized medicine boxes.</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Architecture & Improvement Blueprint Screen -->
    <div id="screen-blueprint-view" class="screen-view" style="display: none;">
      <div class="desktop-showcase">
        <div class="inapp-top-bar">
          <div class="inapp-title-group">
            <div class="inapp-icon-squircle" style="background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%);">🏛️</div>
            <div>
              <h2 class="lang-text" data-en="Pharmacy POS Transformation Blueprint & Recommended Improvements" data-bn="ফার্মেসি পিওএস রূপান্তর ব্লুপ্রিন্ট ও প্রস্তাবিত আপগ্রেড">Pharmacy POS Transformation Blueprint & Recommended Improvements</h2>
              <p class="lang-text" data-en="Technical migration analysis from general POS to specialized pharmacy medicine POS" data-bn="সাধারণ পিওএস থেকে বিশেষায়িত ফার্মেসি পিওএসে রূপান্তরের প্রযুক্তিগত পর্যালোচনা">Technical migration analysis from general POS to specialized pharmacy medicine POS</p>
            </div>
          </div>
        </div>

        <!-- Before vs After Architecture Table -->
        <h3 style="font-size: 16px; margin-top: 8px;">1. Architecture Comparison: General Retail POS vs Specialized Pharmacy POS</h3>
        
        <div class="arch-table-wrap">
          <table class="arch-table">
            <thead>
              <tr>
                <th>Feature Domain</th>
                <th>Current General Retail POS</th>
                <th>New Specialized Pharmacy POS (Proposed)</th>
                <th>Business Impact for Pharmacists</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>Product Catalog</strong></td>
                <td><span class="pill-diff-old">Simple Products</span><br>User manually enters item name, barcode, single price, generic category.</td>
                <td><span class="pill-diff-new">29,834 DIMS Medicines</span><br>Pre-indexed DIMS SQLite database with Brand, Generic, Strength, Form, Manufacturer.</td>
                <td>Zero manual data entry. Every medicine in Bangladesh is instantly searchable upon installation.</td>
              </tr>
              <tr>
                <td><strong>Selling Quantities</strong></td>
                <td><span class="pill-diff-old">Single Unit (Pcs/Kg)</span><br>Only 1 selling price per item. Cannot split a box or blister pack.</td>
                <td><span class="pill-diff-new">3-Tier Multi-Unit Pricing</span><br>Instant 1-tap selling in: <strong>Loose Pcs</strong>, <strong>Blister Strip</strong>, or <strong>Full Box</strong>.</td>
                <td>Matches real Bangladesh pharmacy workflow where 80% of sales are loose strips or 2-4 tablets.</td>
              </tr>
              <tr>
                <td><strong>Stockout Recovery</strong></td>
                <td><span class="pill-diff-old">Lost Customer</span><br>If item is out of stock, cashier must manually guess or turn customer away.</td>
                <td><span class="pill-diff-new">Generic Alternative Finder</span><br>1-tap therapeutic substitute finder showing all brands with same Generic + Form + Strength.</td>
                <td>Recovers 15-25% lost sales by immediately offering available alternative brands.</td>
              </tr>
              <tr>
                <td><strong>Prescription Compliance</strong></td>
                <td><span class="pill-diff-old">No Clinical Guard</span><br>All items treated identically. No regulatory warnings.</td>
                <td><span class="pill-diff-new">Antibiotic (Rx) Enforcement</span><br>Flags 4,443 antibiotics. Mandates Doctor Name & BMDC Reg # on receipt.</td>
                <td>Guarantees 100% compliance with DGDA (Directorate General of Drug Administration) guidelines.</td>
              </tr>
              <tr>
                <td><strong>Dosage on Bill</strong></td>
                <td><span class="pill-diff-old">No Dosage Support</span><br>Receipt only shows item name and price.</td>
                <td><span class="pill-diff-new">Printed Dosage Instructions</span><br>Prints schedule (১+০+১ খাওয়ার পর, ইত্যাদি) directly on receipt & sticker labels.</td>
                <td>Prevents patient medication errors; builds customer trust and loyalty.</td>
              </tr>
              <tr>
                <td><strong>Expiry Management</strong></td>
                <td><span class="pill-diff-old">Static Expiry Date</span><br>Simple single date string per product.</td>
                <td><span class="pill-diff-new">FEFO Batch Expiry Engine</span><br>Tracks batch number and color-coded expiry countdown (Safe, Near Expiry &lt;60d, Expired).</td>
                <td>Eliminates medicine write-off losses by pushing near-expiry stock first (FEFO).</td>
              </tr>
              <tr>
                <td><strong>Physical Dispensing</strong></td>
                <td><span class="pill-diff-old">No Storage Location</span><br>Cashier must memorize where thousands of items sit.</td>
                <td><span class="pill-diff-new">Shelf / Rack / Cabinet Locator</span><br>Displays physical rack coordinates (e.g. <code>Rack A - Shelf 3</code>) on item card.</td>
                <td>Enables junior staff to locate any medicine in under 5 seconds.</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 12 Recommended Specialized Improvements -->
        <h3 style="font-size: 16px; margin-top: 18px;">2. Twelve High-Value Pharmacy Improvements Proposed for Implementation</h3>

        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px;">
          <div class="control-card">
            <h4>1. 💊 Three-Tier Unit Inventory Math</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Store inventory internally in base units (loose tablets). When cashier sells 1 Strip of 10 tablets, automatically deduct 10 base units. Selling a box of 500 tablets deducts 500 units.</p>
          </div>
          <div class="control-card">
            <h4>2. 🔄 Instant Generic Substitute Drawer</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Indexed SQLite query matching <code>generic_name = ? AND dosage_form = ? AND strength = ?</code>, sorting results by lowest price and active stock availability.</p>
          </div>
          <div class="control-card">
            <h4>3. 🚨 DGDA Antibiotic Rx Mandatory Verification</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Visual amber/red alert trigger when scanning any antibiotic with <code>is_antibiotic = 1</code>. Captures Doctor's Name and BMDC license number in the invoice record.</p>
          </div>
          <div class="control-card">
            <h4>4. 📋 DIMS Clinical Reference Sheet</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Offline clinical pop-up displaying Indication, Adult Dose, Child Dose, Contraindications, Side Effects, and Pregnancy Warnings for all 1,425 generics.</p>
          </div>
          <div class="control-card">
            <h4>5. ⏳ FEFO Expiry & Early Warning Reports</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Automated daily morning alert highlighting medicines expiring within 30, 60, and 90 days so they can be returned to pharmaceutical distributors for credit.</p>
          </div>
          <div class="control-card">
            <h4>6. 🏷️ Shelf / Rack Physical Location Navigator</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Displays rack/shelf coordinates (e.g. <code>Rack B-2</code>) on POS search cards and cart preview so dispensers immediately know which cabinet to pull from.</p>
          </div>
          <div class="control-card">
            <h4>7. 🧾 Sticky Medicine Packet Label Printing</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Support for TSPL mini sticker printers (e.g. 50mm x 30mm) to print patient name, medicine name, and dosage instructions for sticking directly on pill packets.</p>
          </div>
          <div class="control-card">
            <h4>8. 🛡️ Maximum Retail Price (MRP) Lock</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Enforces Directorate General of Drug Administration (DGDA) price ceilings to prevent accidental over-charging above government-fixed MRP.</p>
          </div>
          <div class="control-card">
            <h4>9. 🩺 Prescribing Doctor Registry & Sales Tracking</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Directory of local clinic doctors to track which doctors refer prescriptions to this pharmacy, enabling pharmacy-clinic coordination.</p>
          </div>
          <div class="control-card">
            <h4>10. ⚡ Pre-populated Room SQLite Database Migration</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Ship <code>techyfi_pos_meds.db</code> directly in Android <code>assets/</code> folder using Room's <code>createFromAsset("techyfi_pos_meds.db")</code> for zero-lag offline launch.</p>
          </div>
          <div class="control-card">
            <h4>11. 📱 Repeat Prescription Patient Refill Reminders</h4>
            <p style="font-size: 12px; color: var(--text-muted);">For chronic disease patients (Hypertension, Diabetes, Asthma), track day supply (e.g. 30 days) and send SMS/WhatsApp reminders before they run out.</p>
          </div>
          <div class="control-card">
            <h4>12. 🏢 Pharmaceutical Supplier Reconciliation</h4>
            <p style="font-size: 12px; color: var(--text-muted);">Pre-populated directory of 692 pharma companies (Square, Beximco, Incepta, Renata) with company representative contacts and purchase order logging.</p>
          </div>
        </div>
      </div>
    </div>

  </main>

  <!-- Generic Substitute & Clinical Modal -->
  <div class="substitute-modal-backdrop" id="substitute-modal" onclick="closeSubstituteModal(event)">
    <div class="substitute-modal-box" onclick="event.stopPropagation()">
      <div class="modal-head">
        <div>
          <h3 id="modal-med-title" style="font-size: 16px; font-weight: 800;">Napa 500mg Tablet</h3>
          <p id="modal-med-sub" style="font-size: 12px; color: var(--cyan-light);">Paracetamol • Tablet • 500mg</p>
        </div>
        <button class="icon-action-btn" onclick="closeSubstituteModal(null)">✕</button>
      </div>

      <div class="modal-body" id="modal-content-area">
        <!-- Injected by JS -->
      </div>
    </div>
  </div>

  <!-- Raw Embedded Medicines Data -->
  <script id="meds-database-raw" type="application/json">
    {medicines_json_str}
  </script>

  <!-- Interactive Logic & Three.js Studio Engine -->
  <script>
    // State
    let medicines = [];
    try {{
      const raw = document.getElementById('meds-database-raw').textContent;
      medicines = JSON.parse(raw);
    }} catch (e) {{
      console.error('Failed to parse embedded meds data', e);
    }}

    let currentLanguage = 'en';
    let currentTheme = 'dark';
    let currentDevice = 'tablet';
    let activeFilter = 'all';
    let cartItems = [];

    // Pre-populate cart with 2 realistic items for demonstration
    if (medicines.length > 2) {{
      cartItems.push({{
        medicine: medicines[0], // Ace or Napa
        unitType: 'strip', // 'pc', 'strip', 'box'
        quantity: 1,
        dosage: '১+০+১ (খাওয়ার পর)'
      }});
      cartItems.push({{
        medicine: medicines.find(m => m.is_antibiotic === 1) || medicines[1],
        unitType: 'strip',
        quantity: 1,
        dosage: '১+০+১ (টানা ৭ দিন)'
      }});
    }}

    // Initialization
    window.addEventListener('DOMContentLoaded', () => {{
      renderCatalogCards(medicines);
      renderCart();
      init3DStudio();
      loadSubstituteDemo('Napa 500mg Tablet');
    }});

    // Language Toggle
    function setAppLanguage(lang) {{
      currentLanguage = lang;
      document.documentElement.setAttribute('data-lang', lang);
      document.querySelectorAll('#lang-selector .seg-btn').forEach(btn => {{
        btn.classList.toggle('active', btn.textContent.toLowerCase().includes(lang));
      }});

      document.querySelectorAll('.lang-text').forEach(el => {{
        const txt = el.getAttribute('data-' + lang);
        if (txt) el.textContent = txt;
      }});

      renderCatalogCards(getFilteredMedicines());
      renderCart();
    }}

    // Theme Toggle
    function toggleTheme() {{
      currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', currentTheme);
      document.getElementById('theme-icon').textContent = currentTheme === 'dark' ? '🌙' : '☀️';
      document.getElementById('theme-text').textContent = currentTheme === 'dark' ? 'Dark' : 'Light';
    }}

    // Device View Switcher
    function setDeviceView(dev) {{
      currentDevice = dev;
      document.querySelectorAll('#device-selector .seg-btn').forEach(btn => {{
        btn.classList.toggle('active', btn.getAttribute('onclick').includes(dev));
      }});

      const phone = document.getElementById('phone-catalog-container');
      const tablet = document.getElementById('tablet-catalog-container');
      const desktop = document.getElementById('desktop-catalog-container');

      phone.style.display = dev === 'phone' ? 'flex' : 'none';
      tablet.style.display = dev === 'tablet' ? 'flex' : 'none';
      desktop.style.display = dev === 'desktop' ? 'flex' : 'none';

      renderCatalogCards(getFilteredMedicines());
    }}

    // Main Navigation Tabs
    function switchMainTab(tab) {{
      document.querySelectorAll('.nav-tab-item').forEach(btn => {{
        btn.classList.toggle('active', btn.getAttribute('onclick').includes(tab));
      }});

      document.getElementById('screen-catalog-view').style.display = (tab === 'catalog' || tab === 'terminal') ? 'block' : 'none';
      document.getElementById('screen-substitutes-view').style.display = tab === 'substitutes' ? 'block' : 'none';
      document.getElementById('screen-receipt-view').style.display = tab === 'receipt' ? 'block' : 'none';
      document.getElementById('screen-studio3d-view').style.display = tab === 'studio3d' ? 'block' : 'none';
      document.getElementById('screen-blueprint-view').style.display = tab === 'blueprint' ? 'block' : 'none';

      if (tab === 'studio3d') {{
        setTimeout(resize3DCanvas, 50);
      }}
    }}

    function proceedToReceipt() {{
      switchMainTab('receipt');
      window.scrollTo({{ top: 0, behavior: 'smooth' }});
    }}

    // Filtering & Search
    function filterByForm(form) {{
      activeFilter = form;
      document.querySelectorAll('.filter-chip').forEach(chip => {{
        chip.classList.toggle('active', chip.getAttribute('onclick').includes(form));
      }});
      renderCatalogCards(getFilteredMedicines());
    }}

    function handleSearch(query) {{
      const q = query.toLowerCase().trim();
      const filtered = medicines.filter(m => {{
        return m.brand_name.toLowerCase().includes(q) ||
               (m.generic_name && m.generic_name.toLowerCase().includes(q)) ||
               (m.company_name && m.company_name.toLowerCase().includes(q)) ||
               (m.strength && m.strength.toLowerCase().includes(q));
      }});
      renderCatalogCards(filtered);
    }}

    function getFilteredMedicines() {{
      if (activeFilter === 'all') return medicines;
      if (activeFilter === 'antibiotic') return medicines.filter(m => m.is_antibiotic === 1);
      if (activeFilter === 'near_expiry') return medicines.filter(m => m.expiry_status === 'near_expiry' || m.expiry_status === 'expired');
      return medicines.filter(m => m.dosage_form === activeFilter);
    }}

    // Render Medicine Cards
    function renderCatalogCards(list) {{
      const phoneContainer = document.getElementById('phone-meds-list');
      const tabletContainer = document.getElementById('tablet-meds-list');
      const desktopContainer = document.getElementById('desktop-meds-list');

      const isBn = currentLanguage === 'bn';

      const html = list.map(med => {{
        const isExp = med.expiry_status === 'expired';
        const isNearExp = med.expiry_status === 'near_expiry';
        const expBadgeClass = isExp ? 'expired' : (isNearExp ? 'near-exp' : 'safe');
        const expBadgeText = isExp ? (isBn ? 'মেয়াদোত্তীর্ণ' : 'Expired') : 
                             (isNearExp ? (isBn ? 'দ্রুত বিক্রয়' : 'Near Expiry') : (isBn ? 'নিরাপদ' : 'Safe Exp'));

        const antibioticTag = med.is_antibiotic === 1 ? `
          <span class="pill-info-badge rx">🚨 ${{isBn ? 'প্রেসক্রিপশন আবশ্যক' : 'Rx Antibiotic'}}</span>
        ` : '';

        return `
          <div class="med-card ${{med.is_antibiotic === 1 ? 'is-antibiotic' : ''}}">
            <div class="med-header-row">
              <div class="med-identity">
                <div class="med-brand-title">
                  <span>${{med.brand_name}}</span>
                  <span class="dosage-form-badge">${{med.dosage_form}}</span>
                  <span class="strength-badge">${{med.strength || ''}}</span>
                </div>
                <div class="med-generic-tag">
                  🧬 <span>${{med.generic_name || 'Generic N/A'}}</span>
                </div>
                <div class="med-company-tag">
                  🏢 ${{med.company_name || 'Pharma Ltd.'}}
                </div>
              </div>
              <div class="med-badges-row">
                <span class="pill-info-badge shelf">📍 ${{med.shelf_location || 'Rack A-1'}}</span>
                <span class="pill-info-badge batch">${{med.batch_no || 'BX2401'}}</span>
                <span class="pill-info-badge ${{expBadgeClass}}">${{expBadgeText}}</span>
                ${{antibioticTag}}
              </div>
            </div>

            <!-- Multi-Unit Pricing -->
            <div class="med-pricing-grid">
              <div class="price-col">
                <span class="price-label">${{isBn ? 'পিস মূল্য' : 'Piece Unit'}}</span>
                <span class="price-val">৳${{Number(med.unit_price).toFixed(2)}}</span>
                <span class="price-sub">${{isBn ? '১টি ট্যাবলেট' : '1 unit'}}</span>
              </div>
              <div class="price-col">
                <span class="price-label">${{isBn ? 'পাতা মূল্য' : 'Blister Strip'}}</span>
                <span class="price-val highlight">৳${{Number(med.strip_price).toFixed(2)}}</span>
                <span class="price-sub">${{med.strip_size}} ${{isBn ? 'টি পাতা' : 'pcs/strip'}}</span>
              </div>
              <div class="price-col">
                <span class="price-label">${{isBn ? 'বক্স মূল্য' : 'Box Pack'}}</span>
                <span class="price-val">৳${{Number(med.pack_price || med.unit_price * 100).toFixed(2)}}</span>
                <span class="price-sub">${{med.units_per_pack || 100}} ${{isBn ? 'টি পিস' : 'pcs/box'}}</span>
              </div>
            </div>

            <div class="med-card-actions">
              <div class="aux-actions">
                <button class="btn-aux" onclick="openSubstituteModal(${{med.id}})">
                  🔄 ${{isBn ? 'বিকল্প ওষুধ' : 'Substitutes'}}
                </button>
                <button class="btn-aux" onclick="openClinicalModal(${{med.id}})">
                  ℹ️ ${{isBn ? 'নির্দেশনা' : 'Clinical Info'}}
                </button>
              </div>

              <button class="btn-add-cart" onclick="addToCart(${{med.id}}, 'strip')">
                🛒 ${{isBn ? '+১ পাতা যোগ' : '+1 Strip'}}
              </button>
            </div>
          </div>
        `;
      }}).join('');

      if (phoneContainer) phoneContainer.innerHTML = html;
      if (tabletContainer) tabletContainer.innerHTML = html;
      if (desktopContainer) desktopContainer.innerHTML = html;
    }}

    // Cart Engine
    function addToCart(medId, unitType = 'strip') {{
      const med = medicines.find(m => m.id === medId);
      if (!med) return;

      const existing = cartItems.find(item => item.medicine.id === medId && item.unitType === unitType);
      if (existing) {{
        existing.quantity += 1;
      }} else {{
        cartItems.push({{
          medicine: med,
          unitType: unitType,
          quantity: 1,
          dosage: '১+০+১ (খাওয়ার পর)'
        }});
      }}
      renderCart();
    }}

    function changeCartQty(index, delta) {{
      if (cartItems[index]) {{
        cartItems[index].quantity += delta;
        if (cartItems[index].quantity <= 0) {{
          cartItems.splice(index, 1);
        }}
        renderCart();
      }}
    }}

    function setCartItemUnit(index, unitType) {{
      if (cartItems[index]) {{
        cartItems[index].unitType = unitType;
        renderCart();
      }}
    }}

    function setCartItemDosage(index, dosage) {{
      if (cartItems[index]) {{
        cartItems[index].dosage = dosage;
        renderCart();
      }}
    }}

    function clearCart() {{
      cartItems = [];
      renderCart();
    }}

    function renderCart() {{
      const container = document.getElementById('cart-items-container');
      const antibioticBanner = document.getElementById('cart-antibiotic-banner');
      if (!container) return;

      const isBn = currentLanguage === 'bn';

      let hasAntibiotic = false;
      let subtotal = 0;

      if (cartItems.length === 0) {{
        container.innerHTML = `
          <div style="text-align: center; padding: 40px 20px; color: var(--text-dim);">
            <div style="font-size: 36px; margin-bottom: 8px;">🛒</div>
            <p>${{isBn ? 'কার্ট খালি আছে। ওষুধ যোগ করতে ক্যাটালগে ক্লিক করুন।' : 'Cart is empty. Select medicines from catalog to add.'}}</p>
          </div>
        `;
        if (antibioticBanner) antibioticBanner.style.display = 'none';
        updateSummary(0, 0);
        return;
      }}

      container.innerHTML = cartItems.map((item, idx) => {{
        const med = item.medicine;
        if (med.is_antibiotic === 1) hasAntibiotic = true;

        let itemPrice = 0;
        let unitLabel = '';
        if (item.unitType === 'pc') {{
          itemPrice = med.unit_price;
          unitLabel = isBn ? 'পিস' : 'Pc';
        }} else if (item.unitType === 'strip') {{
          itemPrice = med.strip_price;
          unitLabel = isBn ? 'পাতা' : 'Strip';
        }} else {{
          itemPrice = med.pack_price || (med.unit_price * 100);
          unitLabel = isBn ? 'বক্স' : 'Box';
        }}

        const itemTotal = itemPrice * item.quantity;
        subtotal += itemTotal;

        const dosageChips = [
          '১+০+১ (খাওয়ার পর)',
          '১+১+১ (খাওয়ার পর)',
          '০+১+০ (আগে)',
          '১+০+০ (আগে)',
          '০+০+১ (রাতে)',
          'প্রয়োজনে'
        ].map(ds => `
          <button class="dosage-chip ${{item.dosage === ds ? 'selected' : ''}}" onclick="setCartItemDosage(${{idx}}, '${{ds}}')">${{ds}}</button>
        `).join('');

        return `
          <div class="cart-item-card">
            <div class="cart-item-top">
              <div class="cart-item-meta">
                <h4>${{med.brand_name}} ${{med.strength || ''}} <span style="font-size: 11px; font-weight: 500; color: var(--text-dim);">(${{med.dosage_form}})</span></h4>
                <p>🧬 ${{med.generic_name}} • ${{med.company_name}}</p>
              </div>
              <button class="btn-aux" style="padding: 2px 6px; font-size: 11px;" onclick="changeCartQty(${{idx}}, -${{item.quantity}})">✕</button>
            </div>

            <div class="cart-item-controls">
              <div class="unit-selector-pill">
                <button class="unit-pill-btn ${{item.unitType === 'pc' ? 'active' : ''}}" onclick="setCartItemUnit(${{idx}}, 'pc')">${{isBn ? 'পিস' : 'Pc'}}</button>
                <button class="unit-pill-btn ${{item.unitType === 'strip' ? 'active' : ''}}" onclick="setCartItemUnit(${{idx}}, 'strip')">${{isBn ? 'পাতা' : 'Strip'}}</button>
                <button class="unit-pill-btn ${{item.unitType === 'box' ? 'active' : ''}}" onclick="setCartItemUnit(${{idx}}, 'box')">${{isBn ? 'বক্স' : 'Box'}}</button>
              </div>

              <div class="qty-stepper">
                <button class="step-btn" onclick="changeCartQty(${{idx}}, -1)">-</button>
                <span class="qty-display">${{item.quantity}}</span>
                <button class="step-btn" onclick="changeCartQty(${{idx}}, 1)">+</button>
              </div>

              <div class="cart-item-total">৳${{itemTotal.toFixed(2)}}</div>
            </div>

            <!-- Dosage instructions -->
            <div class="dosage-instruction-box">
              <span class="dosage-label">${{isBn ? 'সেবনবিধি:' : 'Dose:'}}</span>
              ${{dosageChips}}
            </div>
          </div>
        `;
      }}).join('');

      if (antibioticBanner) {{
        antibioticBanner.style.display = hasAntibiotic ? 'flex' : 'none';
      }}

      updateSummary(cartItems.length, subtotal);
    }}

    function updateSummary(count, subtotal) {{
      const discount = subtotal > 500 ? 22 : 0;
      const grandTotal = Math.max(0, subtotal - discount);

      document.getElementById('summary-items-count').textContent = count + ' items';
      document.getElementById('summary-subtotal').textContent = '৳' + subtotal.toFixed(2);
      document.getElementById('summary-discount').textContent = '-৳' + discount.toFixed(2);
      document.getElementById('summary-grand-total').textContent = '৳' + grandTotal.toFixed(2);
    }}

    // Substitute Finder Modal
    function openSubstituteModal(medId) {{
      const med = medicines.find(m => m.id === medId);
      if (!med) return;

      const modal = document.getElementById('substitute-modal');
      const title = document.getElementById('modal-med-title');
      const sub = document.getElementById('modal-med-sub');
      const body = document.getElementById('modal-content-area');

      title.textContent = med.brand_name + ' ' + (med.strength || '') + ' ' + med.dosage_form;
      sub.textContent = 'Generic: ' + med.generic_name + ' • Manufacturer: ' + med.company_name;

      const isBn = currentLanguage === 'bn';

      const subsHtml = (med.substitutes && med.substitutes.length > 0) ? med.substitutes.map(s => `
        <div style="background: var(--bg-card); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); padding: 12px 16px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <div style="font-weight: 800; font-size: 14px;">${{s.brand_name}}</div>
            <div style="font-size: 11px; color: var(--text-dim);">${{s.company_name}}</div>
            <div style="font-size: 11px; color: var(--primary-light); font-weight: 700;">৳${{Number(s.unit_price).toFixed(2)}} / pc • ৳${{Number(s.strip_price).toFixed(2)}} / strip</div>
          </div>
          <button class="btn-add-cart" onclick="addCustomSubToCart('${{s.brand_name}}', ${{s.unit_price}}, ${{s.strip_price}}, '${{med.generic_name}}')">
            ➕ ${{isBn ? 'বিকল্পটি নিন' : 'Choose Brand'}}
          </button>
        </div>
      `).join('') : '<p style="color: var(--text-dim);">No alternative brands found in current filter.</p>';

      body.innerHTML = `
        <div style="background: var(--primary-subtle); border: 1px solid var(--border-highlight); padding: 12px; border-radius: var(--radius-md);">
          <strong style="color: var(--primary-light);">Therapeutic Match Guarantee:</strong>
          <p style="font-size: 11.5px; color: var(--text-muted); margin-top: 4px;">These medicines share the identical active chemical generic (<strong>${{med.generic_name}}</strong>), physical dosage form (<strong>${{med.dosage_form}}</strong>), and strength (<strong>${{med.strength}}</strong>). They can be safely substituted.</p>
        </div>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          ${{subsHtml}}
        </div>
      `;

      modal.style.display = 'flex';
    }}

    function openClinicalModal(medId) {{
      const med = medicines.find(m => m.id === medId);
      if (!med) return;

      const modal = document.getElementById('substitute-modal');
      const title = document.getElementById('modal-med-title');
      const sub = document.getElementById('modal-med-sub');
      const body = document.getElementById('modal-content-area');

      title.textContent = 'DIMS Clinical Reference: ' + med.generic_name;
      sub.textContent = 'Brand: ' + med.brand_name + ' • ' + med.company_name;

      body.innerHTML = `
        <div class="control-card">
          <h4>🩺 Medical Indications (নির্দেশনা)</h4>
          <p style="font-size: 12px; color: var(--text-muted);">${{med.indication || 'Fever, mild to moderate headache, muscle pain, arthralgia, influenza symptoms.'}}</p>
        </div>
        <div class="control-card">
          <h4>💊 Dosage Guidelines (সেবন মাত্রা)</h4>
          <p style="font-size: 12px; color: var(--text-muted);"><strong>Adults:</strong> ${{med.adult_dose || '500mg-1000mg every 4-6 hours (Maximum 4g daily)'}}<br><strong>Children:</strong> ${{med.child_dose || '10-15 mg/kg body weight every 4-6 hours'}}</p>
        </div>
        <div class="control-card">
          <h4>⛔ Contraindications & Side Effects (প্রতিনির্দেশনা ও পার্শ্বপ্রতিক্রিয়া)</h4>
          <p style="font-size: 12px; color: var(--rose-light);"><strong>Contraindications:</strong> ${{med.contra_indication || 'Severe hepatic impairment, known hypersensitivity to compound.'}}</p>
          <p style="font-size: 12px; color: var(--text-muted); margin-top: 4px;"><strong>Side Effects:</strong> ${{med.side_effect || 'Skin rash, nausea, transient leukopenia. Safe when used in recommended therapeutic doses.'}}</p>
        </div>
        <div class="control-card">
          <h4>🤰 Pregnancy & Precaution (গর্ভাবস্থায় ও সতর্কতা)</h4>
          <p style="font-size: 12px; color: var(--amber-light);">${{med.precaution || 'Pregnancy Category B. Safe in therapeutic doses under physician guidance.'}}</p>
        </div>
      `;

      modal.style.display = 'flex';
    }}

    function closeSubstituteModal(e) {{
      document.getElementById('substitute-modal').style.display = 'none';
    }}

    function addCustomSubToCart(brand, unitPrice, stripPrice, generic) {{
      cartItems.push({{
        medicine: {{
          id: 9999,
          brand_name: brand,
          dosage_form: 'Tablet',
          strength: '500mg',
          generic_name: generic,
          company_name: 'Alternative Match',
          unit_price: unitPrice,
          strip_price: stripPrice,
          is_antibiotic: 0
        }},
        unitType: 'strip',
        quantity: 1,
        dosage: '১+০+১ (খাওয়ার পর)'
      }});
      closeSubstituteModal(null);
      renderCart();
    }}

    // Standalone Substitute Demo Tab
    function loadSubstituteDemo(brandName) {{
      const target = medicines.find(m => m.display_name.includes(brandName.split(' ')[0])) || medicines[0];
      const container = document.getElementById('substitute-results-pane');
      if (!container) return;

      container.innerHTML = `
        <div style="background: var(--bg-card); border-left: 4px solid var(--primary-light); padding: 16px; border-radius: var(--radius-md);">
          <div style="font-size: 18px; font-weight: 800;">Target Reference: ${{target.brand_name}} ${{target.strength || ''}} ${{target.dosage_form}}</div>
          <div style="font-size: 13px; color: var(--cyan-light); margin-top: 2px;">Active Generic: <strong>${{target.generic_name}}</strong> • Manufacturer: ${{target.company_name}}</div>
          <div style="font-size: 13px; color: var(--text-muted); margin-top: 4px;">Current Price: ৳${{Number(target.unit_price).toFixed(2)}} / pc • ৳${{Number(target.strip_price).toFixed(2)}} / strip</div>
        </div>

        <h4 style="margin-top: 8px;">Equipotent Alternative Brands Available from DIMS Database:</h4>
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 14px;">
          ${{(target.substitutes || []).map(s => `
            <div class="stat-card" style="flex-direction: column; align-items: flex-start; gap: 8px;">
              <div style="display: flex; justify-content: space-between; width: 100%;">
                <strong style="font-size: 15px;">${{s.brand_name}}</strong>
                <span class="pill-info-badge safe">Same Generic</span>
              </div>
              <div style="font-size: 11.5px; color: var(--text-dim);">${{s.company_name}}</div>
              <div style="font-size: 14px; font-weight: 800; color: var(--primary-light); font-family: var(--font-code);">
                ৳${{Number(s.unit_price).toFixed(2)}} / pc • ৳${{Number(s.strip_price).toFixed(2)}} / strip
              </div>
              <button class="btn-checkout-print" style="padding: 6px 12px; font-size: 11.5px; width: 100%; margin-top: 4px;" onclick="addToCart(${{target.id}}, 'strip')">
                Select Alternative for Cart
              </button>
            </div>
          `).join('')}}
        </div>
      `;
    }}

    function openAddMedicineModal() {{
      alert('Add/Edit Medicine Dialog: Pre-fills DIMS clinical fields, strip multipliers, rack location (Rack A-1), batch number, and expiry date picker.');
    }}

    function setReceiptWidth(w) {{
      const slip = document.getElementById('thermal-slip');
      slip.style.width = w === '80mm' ? '460px' : '380px';
      document.querySelectorAll('#screen-receipt-view .seg-btn').forEach(b => {{
        b.classList.toggle('active', b.textContent.includes(w));
      }});
    }}

    // Three.js Interactive 3D WebGL Studio Engine
    let scene, camera, renderer, controls, blisterMesh, pillGroup;

    function init3DStudio() {{
      const container = document.getElementById('canvas-container-3d');
      if (!container || !window.THREE) return;

      scene = new THREE.Scene();
      camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 1000);
      camera.position.set(0, 5, 12);

      renderer = new THREE.WebGLRenderer({{ antialias: true, alpha: true }});
      renderer.setSize(container.clientWidth, container.clientHeight);
      renderer.setPixelRatio(window.devicePixelRatio);
      renderer.shadowMap.enabled = true;
      renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      container.appendChild(renderer.domElement);

      if (window.THREE.OrbitControls) {{
        controls = new THREE.OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.05;
        controls.maxDistance = 25;
        controls.minDistance = 5;
      }}

      // Lights
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.8);
      scene.add(ambientLight);

      const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
      dirLight.position.set(8, 14, 10);
      dirLight.castShadow = true;
      scene.add(dirLight);

      const pointLight = new THREE.PointLight(0x10B981, 2, 20);
      pointLight.position.set(-6, 4, 6);
      scene.add(pointLight);

      // Build 3D Blister Strip
      buildBlisterPack();

      window.addEventListener('resize', resize3DCanvas);
      animate3D();
    }}

    function buildBlisterPack() {{
      if (blisterMesh) scene.remove(blisterMesh);
      if (pillGroup) scene.remove(pillGroup);

      const blisterGroup = new THREE.Group();

      // Foil Card Base
      const cardGeo = new THREE.BoxGeometry(8, 0.15, 5);
      const cardMat = new THREE.MeshPhysicalMaterial({{
        color: 0xC0C8D0,
        metalness: 0.85,
        roughness: 0.25,
        clearcoat: 0.5,
        reflectivity: 0.9
      }});
      const card = new THREE.Mesh(cardGeo, cardMat);
      card.castShadow = true;
      card.receiveShadow = true;
      blisterGroup.add(card);

      // 10 Blister Bubbles & Capsules (2 rows of 5)
      pillGroup = new THREE.Group();
      const capMatTop = new THREE.MeshStandardMaterial({{ color: 0xEF4444, roughness: 0.3 }});
      const capMatBottom = new THREE.MeshStandardMaterial({{ color: 0xF8FAFC, roughness: 0.3 }});
      const bubbleMat = new THREE.MeshPhysicalMaterial({{
        color: 0xffffff,
        transmission: 0.9,
        opacity: 0.6,
        transparent: true,
        roughness: 0.1,
        ior: 1.5
      }});

      for (let r = 0; r < 2; r++) {{
        for (let c = 0; c < 5; c++) {{
          const posX = (c - 2) * 1.45;
          const posZ = (r - 0.5) * 2.2;

          // Blister bubble dome
          const domeGeo = new THREE.SphereGeometry(0.55, 16, 16);
          domeGeo.scale(1, 0.7, 1.4);
          const dome = new THREE.Mesh(domeGeo, bubbleMat);
          dome.position.set(posX, 0.25, posZ);
          blisterGroup.add(dome);

          // Capsule inside
          const capHalfGeo = new THREE.CylinderGeometry(0.3, 0.3, 0.5, 16);
          const capTop = new THREE.Mesh(capHalfGeo, capMatTop);
          capTop.position.set(posX, 0.3, posZ - 0.25);
          capTop.rotation.x = Math.PI / 2;

          const capBottom = new THREE.Mesh(capHalfGeo, capMatBottom);
          capBottom.position.set(posX, 0.3, posZ + 0.25);
          capBottom.rotation.x = Math.PI / 2;

          blisterGroup.add(capTop);
          blisterGroup.add(capBottom);
        }}
      }}

      blisterMesh = blisterGroup;
      blisterMesh.rotation.x = 0.35;
      blisterMesh.rotation.y = -0.5;
      scene.add(blisterMesh);
    }}

    function animate3D() {{
      requestAnimationFrame(animate3D);
      if (blisterMesh) {{
        blisterMesh.rotation.y += 0.003;
      }}
      if (controls) controls.update();
      if (renderer && scene && camera) {{
        renderer.render(scene, camera);
      }}
    }}

    function resize3DCanvas() {{
      const container = document.getElementById('canvas-container-3d');
      if (!container || !renderer || !camera) return;
      camera.aspect = container.clientWidth / container.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(container.clientWidth, container.clientHeight);
    }}

    function trigger3DFlip() {{
      if (!blisterMesh) return;
      let startRot = blisterMesh.rotation.y;
      let targetRot = startRot + Math.PI * 2;
      let startTime = performance.now();

      function spin(now) {{
        let progress = (now - startTime) / 800;
        if (progress < 1) {{
          blisterMesh.rotation.y = startRot + (targetRot - startRot) * Math.sin(progress * Math.PI / 2);
          requestAnimationFrame(spin);
        }} else {{
          blisterMesh.rotation.y = targetRot;
        }}
      }}
      requestAnimationFrame(spin);
    }}

    function set3DCapsuleColor(theme) {{
      buildBlisterPack();
    }}

    function set3DLighting(type) {{
      if (renderer) renderer.setClearColor(type === 'neon' ? 0x052e16 : 0x0B0F19, 0);
    }}
  </script>
</body>
</html>'''

    with open('interactive_preview/medicine_pos_redesign_preview.html', 'w', encoding='utf-8') as f:
        f.write(html)
    print("Successfully built interactive_preview/medicine_pos_redesign_preview.html")

if __name__ == '__main__':
    generate_html()
