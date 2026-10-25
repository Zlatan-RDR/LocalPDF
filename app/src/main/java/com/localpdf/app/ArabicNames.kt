package com.localpdf.app

// Arabic display names, search keywords and field labels for the Stirling-PDF tools.
// Anything not listed here simply keeps its English title/label from the API.
object ArabicNames {

    // key = last part of the endpoint (see Catalog.endpointKey)
    // value = "Arabic name|extra search keywords"
    private val tools: Map<String, String> = mapOf(
        // analysis
        "annotation-info" to "معلومات التعليقات التوضيحية|تعليقات ملاحظات",
        "basic-info" to "معلومات أساسية عن الملف|تحليل",
        "document-properties" to "خصائص المستند|بيانات وصفية",
        "font-info" to "معلومات الخطوط|خط",
        "form-fields" to "حقول النموذج (تحليل)|استمارة",
        "page-count" to "عدد صفحات الملف|كم صفحة",
        "page-dimensions" to "أبعاد الصفحات|مقاس حجم",
        "security-info" to "معلومات الأمان والتشفير|حماية",

        // convert
        "cbr-to-pdf" to "كتاب CBR إلى PDF|كوميك",
        "cbz-to-pdf" to "كتاب CBZ إلى PDF|كوميك",
        "ebook-to-pdf" to "كتاب إلكتروني إلى PDF|epub",
        "eml-to-pdf" to "رسالة بريد EML إلى PDF|ايميل",
        "file-to-pdf" to "ملف (Word، Excel...) إلى PDF|وورد اكسل تحويل",
        "html-to-pdf" to "صفحة HTML إلى PDF|ويب",
        "img-to-pdf" to "صور إلى PDF|صورة تحويل",
        "markdown-to-pdf" to "ملف Markdown إلى PDF|md",
        "pdf-to-cbr" to "PDF إلى CBR|كوميك",
        "pdf-to-cbz" to "PDF إلى CBZ|كوميك",
        "pdf-to-csv" to "PDF إلى CSV (جداول)|جدول اكسل",
        "pdf-to-epub" to "PDF إلى كتاب EPUB|كتاب",
        "pdf-to-html" to "PDF إلى HTML|ويب",
        "pdf-to-img" to "PDF إلى صور|صورة png jpg",
        "pdf-to-markdown" to "PDF إلى Markdown|md",
        "pdf-to-pdfa" to "PDF إلى PDF/A (أرشفة)|ارشفة حفظ طويل",
        "pdf-to-presentation" to "PDF إلى عرض تقديمي|باوربوينت شرائح",
        "pdf-to-rtf" to "PDF إلى RTF|نص",
        "pdf-to-text" to "PDF إلى نص|استخراج نص txt",
        "pdf-to-text-editor" to "تحرير نص PDF (تحويل للتحرير)|محرر",
        "pdf-to-ua" to "PDF إلى PDF/UA (وصول)|امكانية الوصول",
        "pdf-to-vector" to "PDF إلى رسوم متجهة|svg",
        "pdf-to-word" to "PDF إلى Word|وورد docx",
        "pdf-to-xlsx" to "PDF إلى Excel|اكسل جدول",
        "pdf-to-xml" to "PDF إلى XML|",
        "svg-to-pdf" to "ملف SVG إلى PDF|رسوم",
        "text-editor-to-pdf" to "نتيجة محرر النص إلى PDF|محرر",
        "url-to-pdf" to "رابط موقع إلى PDF|ويب صفحة",
        "vector-to-pdf" to "رسوم متجهة إلى PDF|svg",

        // filter
        "filter-contains-image" to "فحص: هل يحتوي على صور؟|فلتر",
        "filter-contains-text" to "فحص: هل يحتوي على نص؟|فلتر",
        "filter-file-size" to "فحص: حجم الملف|فلتر",
        "filter-page-count" to "فحص: عدد الصفحات|فلتر",
        "filter-page-rotation" to "فحص: دوران الصفحات|فلتر",
        "filter-page-size" to "فحص: مقاس الصفحة|فلتر",

        // form
        "add-fields" to "إضافة حقول نموذج|استمارة",
        "delete-fields" to "حذف حقول النموذج|استمارة",
        "edit-fields" to "تعديل حقول النموذج|استمارة",
        "extract-csv" to "استخراج بيانات النموذج إلى CSV|استمارة",
        "extract-xlsx" to "استخراج بيانات النموذج إلى Excel|استمارة",
        "fields" to "قائمة حقول النموذج|استمارة",
        "fields-with-coordinates" to "حقول النموذج مع الإحداثيات|استمارة",
        "fill" to "تعبئة النموذج|استمارة",
        "modify-fields" to "تعديل خصائص حقول النموذج|استمارة",

        // general
        "booklet-imposition" to "ترتيب الصفحات ككتيّب للطباعة|طباعة كتيب",
        "crop" to "قص حواف الصفحات|اقتطاع",
        "edit-table-of-contents" to "تعديل فهرس المحتويات|فهرس",
        "edit-text" to "تعديل النص داخل PDF|استبدال",
        "extract-bookmarks" to "استخراج الإشارات المرجعية|فهرس",
        "merge-pdfs" to "دمج ملفات PDF|ضم جمع",
        "split-pages" to "تقسيم حسب أرقام الصفحات|فصل قص",
        "split-pdf-by-sections" to "تقسيم إلى أقسام|فصل",
        "split-by-size-or-count" to "تقسيم حسب الحجم أو العدد|فصل",
        "split-pdf-by-chapters" to "تقسيم حسب الفصول|فصل",
        "auto-split-pdf" to "تقسيم تلقائي بورقة فاصلة|فصل",
        "rotate-pdf" to "تدوير الصفحات|دوران قلب",
        "remove-pages" to "حذف صفحات|ازالة",
        "rearrange-pages" to "إعادة ترتيب الصفحات|ترتيب",
        "multi-page-layout" to "عدة صفحات في ورقة واحدة|تجميع",
        "scale-pages" to "تغيير مقياس الصفحات|حجم تكبير تصغير",
        "pdf-to-single-page" to "كل الصفحات في صفحة واحدة|دمج صفحات",
        "overlay-pdfs" to "وضع PDF فوق PDF|تراكب",

        // misc
        "compress-pdf" to "ضغط PDF وتصغير الحجم|تصغير حجم",
        "ocr-pdf" to "التعرف على النص (OCR)|مسح ضوئي قراءة",
        "extract-images" to "استخراج الصور من PDF|صور",
        "extract-image-scans" to "استخراج الصور الممسوحة|مسح",
        "add-image" to "إضافة صورة إلى PDF|صورة",
        "add-page-numbers" to "ترقيم الصفحات|رقم",
        "add-stamp" to "إضافة ختم|ختم",
        "auto-rename" to "إعادة تسمية تلقائية|اسم",
        "update-metadata" to "تعديل البيانات الوصفية (عنوان، مؤلف)|metadata",
        "flatten" to "تسطيح PDF (تثبيت النماذج)|فلاتن",
        "remove-blanks" to "حذف الصفحات الفارغة|فارغة بيضاء",
        "remove-annotations" to "حذف التعليقات التوضيحية|تعليقات",
        "remove-image-pdf" to "حذف الصور من PDF|صور ازالة",
        "repair" to "إصلاح ملف PDF|تالف اصلاح",
        "replace-invert-pdf-color" to "عكس أو استبدال ألوان PDF|لون",
        "show-javascript" to "إظهار أكواد JavaScript|جافا سكريبت",
        "unlock-pdf-forms" to "فتح قفل نماذج PDF|استمارة",
        "adjust-contrast" to "ضبط التباين والسطوع|لون",
        "decompress-pdf" to "فك ضغط PDF|",
        "scanner-effect" to "تأثير الماسح الضوئي|مسح",
        "get-info-on-pdf" to "معلومات كاملة عن PDF|تحليل",

        // security
        "add-password" to "إضافة كلمة مرور|تشفير حماية",
        "remove-password" to "إزالة كلمة المرور|فك تشفير",
        "add-watermark" to "إضافة علامة مائية|شعار",
        "cert-sign" to "توقيع بشهادة رقمية|توقيع",
        "remove-cert-sign" to "إزالة توقيع الشهادة|توقيع",
        "sanitize-pdf" to "تنقية PDF (حذف المحتوى الخفي)|تنظيف خصوصية",
        "auto-redact" to "حجب تلقائي للنصوص|تعتيم",
        "redact" to "حجب يدوي للمحتوى|تعتيم",
        "validate-signature" to "التحقق من التوقيع|توقيع"
    )

    private val fields: Map<String, String> = mapOf(
        "fileInput" to "الملف",
        "comparator" to "طريقة المقارنة",
        "pageCount" to "عدد الصفحات",
        "angle" to "زاوية الدوران (من مضاعفات 90)",
        "pageNumbers" to "أرقام الصفحات (مثل all أو 1,3,5-9)",
        "imageFormat" to "صيغة الصورة",
        "singleOrMultiple" to "صورة واحدة أم عدة صور",
        "colorType" to "نوع اللون",
        "dpi" to "الدقة DPI",
        "includeAnnotations" to "تضمين التعليقات",
        "splitType" to "نوع التقسيم (0 حجم، 1 صفحات، 2 عدد المستندات)",
        "splitValue" to "قيمة التقسيم (مثل 10MB أو 5)",
        "optimizeLevel" to "مستوى الضغط",
        "expectedOutputSize" to "الحجم المتوقع (مثل 25KB)",
        "linearize" to "تحسين للعرض على الويب",
        "normalize" to "توحيد المحتوى",
        "grayscale" to "تدرج رمادي",
        "lineArt" to "تحويل الصور إلى رسم خطي",
        "lineArtThreshold" to "حد الرسم الخطي (0-100)",
        "lineArtEdgeLevel" to "قوة كشف الحواف (1-3)",
        "watermarkType" to "نوع العلامة (text أو image)",
        "watermarkText" to "نص العلامة المائية",
        "watermarkImage" to "صورة العلامة المائية",
        "alphabet" to "الأبجدية (roman، arabic...)",
        "fontSize" to "حجم الخط",
        "rotation" to "زاوية الدوران",
        "opacity" to "الشفافية (من 0 إلى 1)",
        "widthSpacer" to "التباعد الأفقي",
        "heightSpacer" to "التباعد العمودي",
        "customColor" to "اللون (مثل #d3d3d3)",
        "convertPDFToImage" to "تحويل النتيجة إلى صورة",
        "sortType" to "نوع الترتيب قبل الدمج",
        "removeCertSign" to "إزالة توقيعات الشهادة",
        "generateToc" to "إنشاء فهرس محتويات",
        "clientFileIds" to "معرّفات الملفات",
        "fitOption" to "طريقة ملاءمة الصورة للصفحة",
        "autoRotate" to "تدوير تلقائي",
        "format" to "الصيغة",
        "password" to "كلمة المرور"
    )

    fun groupTitle(g: String): String = when (g) {
        "analysis" -> "تحليل"
        "convert" -> "تحويل"
        "filter" -> "فلترة"
        "form" -> "نماذج"
        "general" -> "عام"
        "misc" -> "متفرقات"
        "security" -> "أمان"
        "pipeline" -> "أتمتة"
        else -> Catalog.humanize(g)
    }

    private fun entry(t: Tool): String? = tools[Catalog.endpointKey(t.path)]

    fun toolName(t: Tool): String {
        val e = entry(t) ?: return t.title
        return e.substringBefore('|')
    }

    private fun keywordsOf(t: Tool): String {
        val e = entry(t) ?: return ""
        return if (e.contains('|')) e.substringAfter('|') else ""
    }

    fun fieldLabel(f: ToolField): String {
        val ar = fields[f.name] ?: return f.label
        return ar + " (" + f.label + ")"
    }

    // Removes Arabic diacritics and unifies common letter variants for searching.
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val c = when (ch) {
                'أ', 'إ', 'آ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                else -> ch
            }
            if (c in '\u064B'..'\u0652' || c == '\u0640') continue
            sb.append(c)
        }
        return sb.toString()
    }

    fun matches(t: Tool, query: String): Boolean {
        val q = normalize(query).trim()
        if (q.isEmpty()) return true
        val hay = normalize(
            toolName(t) + " " + keywordsOf(t) + " " + t.title + " " + t.path + " " + groupTitle(t.group)
        )
        return q.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { hay.contains(it) }
    }
}
