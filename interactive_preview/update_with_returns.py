import os

script_path = "/Users/shahidulhaque/App Dev/Techyfi POS/interactive_preview/generate_3d_preview.py"

with open(script_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add CSS for Return Alert Card and 3D Return Icon
css_addition = '''
    /* 8. REALISTIC 3D ANIMATED RETURN / REVERSAL ORB */
    .realistic-3d-return-icon {
      width: 30px;
      height: 30px;
      position: relative;
      transform-style: preserve-3d;
      animation: returnOrbFloat 3.5s ease-in-out infinite;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .realistic-3d-return-icon.micro {
      width: 22px;
      height: 22px;
    }

    .realistic-3d-return-icon.large {
      width: 44px;
      height: 44px;
    }

    .return-orb {
      position: absolute;
      inset: 2px;
      border-radius: 50%;
      background: radial-gradient(circle at 35% 30%, #FDA4AF 0%, #F43F5E 50%, #881337 100%);
      box-shadow: 
        0 0 14px rgba(244, 63, 94, 0.7),
        inset 0 1.5px 3px rgba(255, 255, 255, 0.8),
        inset 0 -2px 5px rgba(0, 0, 0, 0.7);
      transform: translateZ(4px);
    }

    .return-arrow-glyph {
      position: absolute;
      font-size: 14px;
      font-weight: 900;
      color: #FFFFFF;
      text-shadow: 0 0 8px rgba(255, 255, 255, 0.9), 0 2px 4px rgba(0,0,0,0.8);
      transform: translateZ(12px);
      animation: returnArrowSpin 4s linear infinite;
    }

    .realistic-3d-return-icon.micro .return-arrow-glyph {
      font-size: 10px;
    }

    .realistic-3d-return-icon.large .return-arrow-glyph {
      font-size: 20px;
    }

    @keyframes returnOrbFloat {
      0%, 100% { transform: translateY(0) rotate(-10deg); }
      50% { transform: translateY(-3px) rotate(10deg); }
    }

    @keyframes returnArrowSpin {
      0% { transform: translateZ(12px) rotate(0deg); }
      100% { transform: translateZ(12px) rotate(-360deg); }
    }

    /* Card Return Alert Badge */
    .card-return-alert {
      background: linear-gradient(135deg, rgba(244, 63, 94, 0.15), rgba(136, 19, 55, 0.15));
      border: 1px solid rgba(244, 63, 94, 0.35);
      border-radius: 10px;
      padding: 6px 10px;
      display: flex;
      align-items: center;
      gap: 9px;
      margin-bottom: 8px;
      font-size: 0.72rem;
      color: #FDA4AF;
      animation: alertGlow 3s ease-in-out infinite;
    }

    @keyframes alertGlow {
      0%, 100% { border-color: rgba(244, 63, 94, 0.35); }
      50% { border-color: rgba(244, 63, 94, 0.7); box-shadow: 0 0 10px rgba(244, 63, 94, 0.25); }
    }

    .return-alert-title {
      font-weight: 800;
      color: #FFE4E6;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .return-alert-sub {
      font-size: 0.65rem;
      color: #FDA4AF;
      opacity: 0.9;
    }

    /* Modal Return Audit Card */
    .modal-return-audit-card {
      background: linear-gradient(135deg, rgba(244, 63, 94, 0.12) 0%, rgba(30, 41, 59, 0.8) 100%);
      border: 1.5px solid rgba(244, 63, 94, 0.45);
      border-radius: 14px;
      padding: 12px 14px;
      margin-bottom: 16px;
    }

    .audit-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .audit-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.78rem;
      font-weight: 800;
      color: #FDA4AF;
    }

    .audit-time {
      font-size: 0.68rem;
      color: var(--text-secondary);
    }

    .audit-item-box {
      background: rgba(0, 0, 0, 0.25);
      border-radius: 8px;
      padding: 8px 10px;
      margin-bottom: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.72rem;
    }

    .audit-profit-deduction {
      font-size: 0.7rem;
      color: #FB7185;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-top: 1px dashed rgba(244, 63, 94, 0.3);
      padding-top: 6px;
    }
'''

# Insert the new CSS before `/* ==========================================================================
# DUAL LAYOUT: MOBILE PHONE VS ARCHITECTURE & 3D STUDIO`
content = content.replace(
    '/* ==========================================================================\n       DUAL LAYOUT: MOBILE PHONE VS ARCHITECTURE & 3D STUDIO',
    css_addition + '\n    /* ==========================================================================\n       DUAL LAYOUT: MOBILE PHONE VS ARCHITECTURE & 3D STUDIO'
)

# 2. Add Returns Filter Chip in pos-sub-toolbar
content = content.replace(
    '<div class="period-chip" onclick="setPeriod(this, \'year\')">This Year</div>',
    '<div class="period-chip" onclick="setPeriod(this, \'year\')">This Year</div>\n          <div class="period-chip chip-returns" id="chipReturnsFilter" onclick="setPeriod(this, \'returns\')" style="color:#FDA4AF;border-color:rgba(244,63,94,0.4);background:rgba(244,63,94,0.1);">↩️ Has Returns (1)</div>'
)

# 3. Add 3D Return Icon in Laboratory Showcase Grid
return_icon_card_html = '''          <!-- Icon 7: 3D Return Reversal Orb -->
          <div class="gallery-icon-card" onclick="selectStudioModel('return')">
            <div class="icon-3d-stage">
              <div class="realistic-3d-return-icon large">
                <div class="return-orb"></div>
                <div class="return-arrow-glyph">↩</div>
              </div>
            </div>
            <span class="gallery-icon-name">3D Return Orb</span>
            <span class="gallery-icon-desc">Volumetric crimson sphere with counter-rotating metallic reversal arrow</span>
          </div>
'''
content = content.replace(
    '<!-- THREE.JS INTERACTIVE 3D WEBGL STUDIO -->',
    return_icon_card_html + '\n        <!-- THREE.JS INTERACTIVE 3D WEBGL STUDIO -->'
)

# 4. Add '↩️ Return Orb' to Studio Model Picker
content = content.replace(
    '<div class="studio-chip" id="chipGem" onclick="selectStudioModel(\'gem\')">🔷 Emerald Gem</div>',
    '<div class="studio-chip" id="chipGem" onclick="selectStudioModel(\'gem\')">🔷 Emerald Gem</div>\n              <div class="studio-chip" id="chipReturn" onclick="selectStudioModel(\'return\')">↩️ Return Orb</div>'
)

# 5. Update JavaScript Mock Invoices to include full return details
old_inv_100 = '''    {
      id: 100,
      time: "Yesterday, 02:10 PM",
      customer: "Mahmud Hasan",
      customerPhone: "01912345678",
      payment: "Due",
      grandTotal: 1400,
      subtotal: 1800,
      discount: 0,
      cogs: 1010,
      profit: 390,
      margin: 27.9,
      markup: 38.6,
      items: [
        { name: "Ergonomic Vertical Mouse", barcode: "890132", qty: 1, price: 1400, cost: 1010, totalRev: 1400, totalCost: 1010, profit: 390, margin: 27.9, isTop: true }
      ],
      discountErosion: 0,
      isReturned: true,
      returnNote: "Original Bill ৳1,800. 1x USB Hub (৳400, Cost ৳260) was returned. Profit adjusted from ৳530 to ৳390."
    }'''

new_inv_100 = '''    {
      id: 100,
      time: "Yesterday, 02:10 PM",
      customer: "Mahmud Hasan",
      customerPhone: "01912345678",
      payment: "Due",
      originalTotal: 1800,
      grandTotal: 1400,
      subtotal: 1800,
      discount: 0,
      cogs: 1010,
      profit: 390,
      originalProfit: 530,
      margin: 27.9,
      markup: 38.6,
      items: [
        { name: "Ergonomic Vertical Mouse", barcode: "890132", qty: 1, price: 1400, cost: 1010, totalRev: 1400, totalCost: 1010, profit: 390, margin: 27.9, isTop: true }
      ],
      discountErosion: 0,
      isReturned: true,
      returnedItems: [
        { name: "USB 3.0 4-Port Hub", barcode: "890133", qty: 1, price: 400, cost: 260, profitLost: 140 }
      ],
      returnRefundAmount: 400,
      returnRefundMethod: "Due Adjustment",
      returnTimestamp: "Yesterday, 03:45 PM",
      returnProcessedBy: "Cashier Ahmed",
      returnNote: "Customer swapped for different model. 1x USB Hub returned, ৳400 due deducted. Stock restored to inventory."
    }'''

content = content.replace(old_inv_100, new_inv_100)

# 6. Update renderInvoices to display the Return Alert Card and support currentPeriod filter
old_render_invoices = '''    sorted.forEach(inv => {'''
new_render_invoices = '''    let currentFilterPeriod = window.selectedPeriod || 'all';
    if (currentFilterPeriod === 'returns') {
      sorted = sorted.filter(inv => inv.isReturned === true);
    }

    sorted.forEach(inv => {'''
content = content.replace(old_render_invoices, new_render_invoices)

# 7. Add return alert badge generation in renderInvoices
old_profit_strip_insert = '''        ${profitStripHtml}

        <div class="item-pills-row">'''

new_profit_strip_insert = '''        ${profitStripHtml}

        ${inv.isReturned ? `
          <div class="card-return-alert">
            <div class="icon-3d-stage">
              <div class="realistic-3d-return-icon micro">
                <div class="return-orb"></div>
                <div class="return-arrow-glyph">↩</div>
              </div>
            </div>
            <div>
              <div class="return-alert-title">
                <span>${currentLang === 'BN' ? 'পণ্য ফেরত সম্পন্ন' : 'Return Items Processed'}</span>
                <span style="font-weight: normal; font-size: 0.65rem;">(1x Item · -৳${inv.returnRefundAmount})</span>
              </div>
              <div class="return-alert-sub">${currentLang === 'BN' ? 'আসল মোট: ৳' + inv.originalTotal + ' ➔ রিফান্ড: -৳' + inv.returnRefundAmount : 'Original: ৳' + inv.originalTotal + ' ➔ Refunded: -৳' + inv.returnRefundAmount}</div>
            </div>
          </div>
        ` : ''}

        <div class="item-pills-row">'''

content = content.replace(old_profit_strip_insert, new_profit_strip_insert)

# 8. Add Return Audit Card in renderProfitAnalytics
old_profit_kpi_grid = '''      <div class="profit-kpi-grid">'''
new_profit_kpi_grid = '''      ${inv.isReturned ? `
        <!-- RETURN AUDIT SECTION -->
        <div class="modal-return-audit-card">
          <div class="audit-top-row">
            <div class="audit-badge">
              <div class="icon-3d-stage">
                <div class="realistic-3d-return-icon micro">
                  <div class="return-orb"></div>
                  <div class="return-arrow-glyph">↩</div>
                </div>
              </div>
              <span>${currentLang === 'BN' ? 'পণ্য ফেরত ও রিফান্ড হিস্ট্রি' : 'Return & Refund Audit'}</span>
            </div>
            <span class="audit-time">${inv.returnTimestamp}</span>
          </div>
          <div class="audit-item-box">
            <div>
              <strong style="color: #FFE4E6;">1x USB 3.0 4-Port Hub</strong><br>
              <span style="font-size: 0.65rem; color: #FDA4AF;">Method: ${inv.returnRefundMethod} • By: ${inv.returnProcessedBy}</span>
            </div>
            <div style="text-align: right; color: #FB7185; font-weight: 800;">
              -৳${inv.returnRefundAmount}<br>
              <span style="font-size: 0.62rem; color: #FDA4AF;">Cost: -৳260</span>
            </div>
          </div>
          <div class="audit-profit-deduction">
            <span>${currentLang === 'BN' ? 'মুনাফা সমন্বয় (রিফান্ডের পর):' : 'Profit Adjustment:'}</span>
            <span><s>৳${inv.originalProfit}</s> ➔ <strong>+৳${inv.profit}</strong> (-৳140 profit lost)</span>
          </div>
          <div style="font-size: 0.65rem; color: #94A3B8; margin-top: 6px; font-style: italic;">
            Note: ${inv.returnNote}
          </div>
        </div>
      ` : ''}

      <div class="profit-kpi-grid">'''

content = content.replace(old_profit_kpi_grid, new_profit_kpi_grid)

# 9. Update setPeriod function
old_set_period = '''  function setPeriod(el, period) {
    document.querySelectorAll('.period-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    play3DMechanicalSound(false);
  }'''

new_set_period = '''  function setPeriod(el, period) {
    document.querySelectorAll('.period-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    window.selectedPeriod = period;
    play3DMechanicalSound(period === 'returns');
    renderInvoices();
  }'''

content = content.replace(old_set_period, new_set_period)

# 10. Add Three.js Model for Return Orb in build3DModel
old_build_model_gem = '''      const gemMesh = new THREE.Mesh(gemGeo, gemMat);
      currentMeshGroup.add(gemMesh);
    }'''

new_build_model_gem = '''      const gemMesh = new THREE.Mesh(gemGeo, gemMat);
      currentMeshGroup.add(gemMesh);
    } else if (type === 'return') {
      document.getElementById('activeModelLabel').textContent = 'Model: 3D Animated Return / Reversal Orb';

      // Crimson Core Sphere
      const sphereGeo = new THREE.SphereGeometry(1.0, 32, 32);
      const sphereMat = new THREE.MeshStandardMaterial({
        color: 0xF43F5E,
        metalness: 0.4,
        roughness: 0.2,
        emissive: 0x881337,
        emissiveIntensity: 0.6,
        wireframe: isWireframe
      });
      const sphereMesh = new THREE.Mesh(sphereGeo, sphereMat);
      currentMeshGroup.add(sphereMesh);

      // Orbiting Return Arrow Ring
      const ringGeo = new THREE.TorusGeometry(1.5, 0.05, 16, 64);
      const ringMat = new THREE.MeshStandardMaterial({
        color: 0xFDA4AF,
        metalness: 0.8,
        roughness: 0.2,
        wireframe: isWireframe
      });
      const ringMesh = new THREE.Mesh(ringGeo, ringMat);
      ringMesh.rotation.x = Math.PI / 2.5;
      currentMeshGroup.add(ringMesh);

      // Reversal Arrow Cone
      const arrowGeo = new THREE.ConeGeometry(0.25, 0.5, 8);
      const arrowMat = new THREE.MeshStandardMaterial({
        color: 0xFFFFFF,
        emissive: 0xFDA4AF,
        wireframe: isWireframe
      });
      const arrowMesh = new THREE.Mesh(arrowGeo, arrowMat);
      arrowMesh.position.set(1.5, 0, 0);
      arrowMesh.rotation.z = -Math.PI / 2;
      currentMeshGroup.add(arrowMesh);
    }'''

content = content.replace(old_build_model_gem, new_build_model_gem)

# 11. Update selectStudioModel for chipReturn
old_select_studio = '''    else if (type === 'gem') document.getElementById('chipGem').classList.add('active');'''
new_select_studio = '''    else if (type === 'gem') document.getElementById('chipGem').classList.add('active');
    else if (type === 'return') document.getElementById('chipReturn').classList.add('active');'''
content = content.replace(old_select_studio, new_select_studio)

# Write updated content back to generate_3d_preview.py
with open(script_path, "w", encoding="utf-8") as f:
    f.write(content)

print("generate_3d_preview.py successfully updated with Return Intelligence!")
