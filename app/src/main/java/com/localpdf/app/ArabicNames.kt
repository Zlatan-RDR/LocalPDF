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
        "encode-charcodes" to "ترميز رموز الخط (أداة تقنية)|محرر",
        "extract-bookmarks" to "استخراج الإشارات المرجعية|فهرس",
        "merge-pdfs" to "دمج ملفات PDF|ضم جمع",
        "multi-page-layout" to "عدة صفحات في ورقة واحدة|تجميع",
        "overlay-pdfs" to "وضع PDF فوق PDF|تراكب",
        "pdf-to-single-page" to "كل الصفحات في صفحة واحدة|دمج صفحات",
        "rearrange-pages" to "إعادة ترتيب الصفحات|ترتيب",
        "remove-image-pdf" to "حذف الصور من PDF|صور ازالة",
        "remove-pages" to "حذف صفحات|ازالة",
        "rotate-pdf" to "تدوير الصفحات|دوران قلب",
        "scale-pages" to "تغيير مقياس الصفحات|حجم تكبير تصغير",
        "split-by-size-or-count" to "تقسيم حسب الحجم أو العدد|فصل",
        "split-for-poster-print" to "تقسيم لطباعة ملصق كبير|بوستر",
        "split-pages" to "تقسيم حسب أرقام الصفحات|فصل قص",
        "split-pdf-by-chapters" to "تقسيم حسب الفصول|فصل",
        "split-pdf-by-sections" to "تقسيم إلى أقسام|فصل",

        // misc
        "add-attachments" to "إضافة مرفقات إلى PDF|مرفق",
        "add-comments" to "إضافة تعليقات|ملاحظات",
        "add-image" to "إضافة صورة إلى PDF|صورة",
        "add-page-numbers" to "ترقيم الصفحات|رقم",
        "add-stamp" to "إضافة ختم|ختم",
        "auto-rename" to "إعادة تسمية تلقائية|اسم",
        "auto-rotate-pdf" to "تدوير تلقائي للصفحات|اتجاه",
        "auto-split-pdf" to "تقسيم تلقائي بورقة فاصلة|فصل",
        "batch-process-attachments" to "معالجة المرفقات دفعة واحدة|مرفق",
        "compress-pdf" to "ضغط PDF وتصغير الحجم|تصغير حجم",
        "create-portfolio" to "إنشاء حافظة ملفات (Portfolio)|ملفات",
        "decompress-pdf" to "فك ضغط PDF|",
        "delete-attachment" to "حذف مرفق|ازالة",
        "extract-attachments" to "استخراج كل المرفقات|مرفق",
        "extract-image-scans" to "استخراج الصور الممسوحة|مسح",
        "extract-images" to "استخراج الصور من PDF|صور",
        "extract-single-attachment" to "استخراج مرفق واحد|مرفق",
        "flatten" to "تسطيح PDF (تثبيت النماذج)|فلاتن",
        "flatten-portfolio" to "تسطيح الحافظة (Portfolio)|ملفات",
        "list-attachments" to "قائمة المرفقات|مرفق",
        "ocr-pdf" to "التعرف على النص (OCR)|مسح ضوئي قراءة",
        "remove-blanks" to "حذف الصفحات الفارغة|فارغة بيضاء",
        "rename-attachment" to "إعادة تسمية مرفق|اسم",
        "repair" to "إصلاح ملف PDF|تالف اصلاح repair",
        "replace-invert-pdf" to "عكس أو استبدال ألوان PDF|لون",
        "scanner-effect" to "تأثير الماسح الضوئي|مسح",
        "show-javascript" to "إظهار أكواد JavaScript|جافا سكريبت",
        "unlock-pdf-forms" to "فتح قفل نماذج PDF|استمارة",
        "update-metadata" to "تعديل البيانات الوصفية (عنوان، مؤلف)|metadata",

        // pipeline
        "handleData" to "تشغيل خط معالجة (Pipeline)|اتمتة",

        // security
        "add-password" to "إضافة كلمة مرور|تشفير حماية",
        "add-watermark" to "إضافة علامة مائية|شعار",
        "auto-redact" to "حجب تلقائي للنصوص|تعتيم",
        "cert-sign" to "توقيع بشهادة رقمية|توقيع",
        "get-info-on-pdf" to "معلومات كاملة عن PDF|تحليل",
        "redact" to "حجب يدوي للمحتوى|تعتيم",
        "redact-execute" to "تنفيذ الحجب|تعتيم",
        "remove-cert-sign" to "إزالة توقيع الشهادة|توقيع",
        "remove-password" to "إزالة كلمة المرور|فك تشفير",
        "sanitize-pdf" to "تنقية PDF (حذف المحتوى الخفي)|تنظيف خصوصية",
        "timestamp-pdf" to "ختم زمني للمستند|توقيت توثيق",
        "validate-compliance" to "التحقق من الامتثال لمعيار محدد|مطابقة pdfa",
        "validate-signature" to "التحقق من التوقيع|توقيع",
        "verify-pdf" to "التحقق من توافق PDF مع المعايير (PDF/A وPDF/UA)|مطابقة فحص سلامة"
    )

    private val fields: Map<String, String> = mapOf(
        // common
        "fileInput" to "الملف",
        "file" to "الملف",
        "files" to "الملفات",
        "data" to "ملف البيانات",
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
        "password" to "كلمة المرور",

        // convert
        "outputFormat" to "صيغة المخرجات (PDF/A أو PDF/X)",
        "pdfUa" to "إعلان PDF/UA (إمكانية الوصول)",
        "strict" to "وضع صارم (يفشل إن لم يتطابق تمامًا)",
        "embedAllFonts" to "تضمين كل الخطوط",
        "includeTableOfContents" to "تضمين فهرس المحتويات",
        "includePageNumbers" to "تضمين أرقام الصفحات",
        "optimizeForEbook" to "تحسين لقراءة الكتب الإلكترونية",
        "zoom" to "التكبير (الافتراضي 1)",
        "includeAttachments" to "تضمين مرفقات البريد",
        "maxAttachmentSizeMB" to "أقصى حجم للمرفق (ميغابايت، من 1 إلى 100)",
        "downloadHtml" to "تنزيل ملف HTML الوسيط بدل PDF",
        "includeAllRecipients" to "تضمين كل المستلمين (CC وBCC)",
        "detectChapters" to "اكتشاف الفصول",
        "targetDevice" to "الجهاز المستهدف",
        "prepress" to "إعدادات ما قبل الطباعة (Ghostscript)",
        "combineIntoSinglePdf" to "دمج كل ملفات SVG في PDF واحد",

        // filter
        "text" to "النص",
        "fileSize" to "حجم الملف (بالبايت)",
        "standardPageSize" to "مقاس الصفحة القياسي",

        // form
        "fields" to "الحقول (JSON)",
        "edits" to "التعديلات (JSON)",
        "names" to "الأسماء (JSON)",
        "updates" to "التحديثات",

        // general
        "pageSize" to "مقاس الصفحة (A0-A6، LETTER، LEGAL، KEEP)",
        "orientation" to "الاتجاه",
        "scaleFactor" to "معامل التكبير",
        "pagesPerSheet" to "عدد الصفحات في الورقة",
        "addBorder" to "إضافة إطار حول الصفحات",
        "spineLocation" to "موضع الكعب (التجليد)",
        "addGutter" to "إضافة هامش تجليد",
        "gutterSize" to "حجم هامش التجليد (نقاط)",
        "doubleSided" to "طباعة على الوجهين",
        "duplexPass" to "تمريرة الطباعة اليدوية على الوجهين",
        "flipOnShortEdge" to "قلب على الحافة القصيرة",
        "x" to "X (الإحداثي الأفقي)",
        "y" to "Y (الإحداثي العمودي)",
        "width" to "العرض",
        "height" to "الارتفاع",
        "removeDataOutsideCrop" to "حذف المحتوى خارج منطقة القص",
        "autoCrop" to "قص تلقائي للمساحات البيضاء",
        "bookmarkData" to "بيانات الإشارات المرجعية (JSON)",
        "replaceExisting" to "استبدال الموجود",
        "wholeWordSearch" to "مطابقة الكلمة كاملة",
        "mode" to "النمط",
        "arrangement" to "ترتيب الصفحات في الورقة",
        "readingDirection" to "اتجاه القراءة",
        "rows" to "عدد الصفوف",
        "cols" to "عدد الأعمدة",
        "innerMargin" to "الهامش الداخلي (نقاط)",
        "topMargin" to "الهامش العلوي (نقاط)",
        "bottomMargin" to "الهامش السفلي (نقاط)",
        "leftMargin" to "الهامش الأيسر (نقاط)",
        "rightMargin" to "الهامش الأيمن (نقاط)",
        "borderWidth" to "سماكة الإطار (نقاط)",
        "overlayFiles" to "ملفات التراكب (PDF)",
        "overlayMode" to "نمط التراكب",
        "counts" to "عدد مرات تكرار كل ملف تراكب",
        "overlayPosition" to "موضع التراكب (0 أمام، 1 خلف)",
        "customMode" to "نمط إعادة الترتيب",
        "rightToLeft" to "التقسيم من اليمين إلى اليسار",
        "xFactor" to "عدد الأعمدة (تقسيم أفقي)",
        "yFactor" to "عدد الصفوف (تقسيم عمودي)",
        "splitMode" to "نمط التقسيم",
        "horizontalDivisions" to "عدد التقسيمات الأفقية لكل صفحة",
        "verticalDivisions" to "عدد التقسيمات العمودية لكل صفحة",
        "merge" to "دمج الناتج في PDF واحد",
        "includeMetadata" to "تضمين البيانات الوصفية",
        "allowDuplicates" to "السماح بالتكرار",
        "bookmarkLevel" to "أعمق مستوى للإشارات المرجعية",

        // misc
        "attachments" to "الملفات المرفقة",
        "convertToPdfA3b" to "تحويل الناتج إلى PDF/A-3b",
        "customMargin" to "الهامش (small, medium, large, x-large)",
        "fontType" to "نوع الخط",
        "fontColor" to "لون الخط (مثل #FF0000)",
        "zeroPad" to "عرض الأصفار البادئة (0 للتعطيل)",
        "position" to "الموضع (من 1 إلى 9 على الصفحة)",
        "startingNumber" to "رقم البداية",
        "pagesToNumber" to "الصفحات المراد ترقيمها (مثل 1,3-5 أو all)",
        "customText" to "نص مخصص ({n} رقم الصفحة، {total} الإجمالي)",
        "stampType" to "نوع الختم (text أو image)",
        "stampText" to "نص الختم",
        "stampImage" to "صورة الختم",
        "overrideX" to "إحداثي X مخصص",
        "overrideY" to "إحداثي Y مخصص",
        "comments" to "التعليقات (JSON)",
        "quality" to "الجودة",
        "colorspace" to "فضاء اللون",
        "border" to "سماكة الإطار (بكسل)",
        "rotate" to "زاوية الدوران الأساسية",
        "rotateVariance" to "تباين الدوران العشوائي",
        "brightness" to "السطوع (1.0 بلا تغيير)",
        "contrast" to "التباين (1.0 بلا تغيير)",
        "blur" to "التمويه",
        "noise" to "التشويش",
        "yellowish" to "محاكاة ورق مصفرّ",
        "resolution" to "الدقة (DPI)",
        "advancedEnabled" to "تفعيل الإعدادات المتقدمة",
        "rotationValue" to "قيمة الدوران",
        "duplexMode" to "وضع الوجهين",
        "opsJson" to "العمليات (JSON)",
        "coverTitle" to "عنوان غلاف الحافظة",
        "attachmentName" to "اسم المرفق",
        "detectionMode" to "طريقة الكشف",
        "confidenceThreshold" to "حد الثقة الأدنى",
        "dryRun" to "تجربة بلا تنفيذ (تقرير فقط)",
        "inferUndetected" to "استنتاج اتجاه الصفحات غير المحسومة",
        "useFirstTextAsFallback" to "استخدام أول نص كعنوان بديل",
        "angleThreshold" to "حد الزاوية",
        "tolerance" to "التسامح",
        "minArea" to "أصغر مساحة",
        "minContourArea" to "أصغر مساحة للحدود",
        "borderSize" to "حجم الإطار",
        "flattenOnlyForms" to "تسطيح النماذج فقط",
        "renderDpi" to "دقة العرض (DPI)",
        "imageFile" to "ملف الصورة",
        "everyPage" to "على كل صفحة",
        "languages" to "اللغات (مثل eng, ara)",
        "sidecar" to "حفظ نص OCR في ملف نصي منفصل",
        "deskew" to "تصحيح ميلان الصفحة",
        "rotatePages" to "تصحيح اتجاه الصفحات تلقائيًا",
        "clean" to "تنظيف الملف المُدخل",
        "cleanFinal" to "تنظيف الناتج النهائي",
        "ocrType" to "نوع OCR (skip-text، force-ocr، Normal)",
        "ocrRenderType" to "طريقة عرض OCR (hocr أو sandwich)",
        "removeImagesAfter" to "حذف الصور من الناتج",
        "threshold" to "الحد",
        "whitePercent" to "نسبة البياض لاعتبار الصفحة فارغة",
        "newName" to "الاسم الجديد",
        "replaceAndInvertOption" to "خيار الاستبدال أو العكس",
        "highContrastColorCombination" to "ألوان التباين العالي",
        "backGroundColor" to "لون الخلفية",
        "textColor" to "لون النص",
        "deleteAll" to "حذف كل البيانات الوصفية",
        "author" to "المؤلف",
        "creationDate" to "تاريخ الإنشاء (yyyy/MM/dd HH:mm:ss)",
        "creator" to "المنشئ",
        "keywords" to "الكلمات المفتاحية",
        "modificationDate" to "تاريخ التعديل (yyyy/MM/dd HH:mm:ss)",
        "producer" to "البرنامج المُنتِج",
        "subject" to "الموضوع",
        "title" to "العنوان",
        "trapped" to "حالة Trapped",
        "json" to "الإعدادات (JSON)",

        // security
        "ownerPassword" to "كلمة مرور المالك (للقيود)",
        "sourcePassword" to "كلمة مرور الملف الأصلي",
        "preserveExisting" to "الإبقاء على كلمات المرور الحالية",
        "keyLength" to "طول مفتاح التشفير",
        "preventAssembly" to "منع تجميع المستند",
        "preventExtractContent" to "منع نسخ المحتوى",
        "preventExtractForAccessibility" to "منع الاستخراج لإمكانية الوصول",
        "preventFillInForm" to "منع تعبئة النماذج",
        "preventModify" to "منع التعديل",
        "preventModifyAnnotations" to "منع تعديل التعليقات",
        "preventPrinting" to "منع الطباعة",
        "preventPrintingFaithful" to "منع الطباعة عالية الدقة",
        "tsaUrl" to "رابط خادم الختم الزمني (TSA)",
        "textValues" to "نصوص للحجب (افصل بفاصلة)",
        "regexPatterns" to "أنماط Regex للحجب (افصل بفاصلة)",
        "wipePages" to "صفحات تُمسح بالكامل (أرقام)",
        "redactImagePages" to "صفحات تُحجب صورها (أرقام)",
        "listOfText" to "قائمة النصوص المراد حجبها",
        "useRegex" to "استخدام Regex",
        "caseSensitive" to "مراعاة حالة الأحرف",
        "redactColor" to "لون الحجب",
        "customPadding" to "حشو إضافي للحجب",
        "pageRedactionColor" to "لون حجب الصفحات الكاملة",
        "removeAll" to "إزالة كل كلمات المرور والقيود",
        "retainedOpeningPassword" to "كلمة مرور الفتح المراد إبقاؤها",
        "removeJavaScript" to "حذف أكواد JavaScript",
        "removeEmbeddedFiles" to "حذف الملفات المضمّنة",
        "removeXMPMetadata" to "حذف بيانات XMP",
        "removeMetadata" to "حذف البيانات الوصفية",
        "removeLinks" to "حذف الروابط",
        "removeFonts" to "حذف الخطوط",
        "certType" to "نوع الشهادة",
        "privateKeyFile" to "ملف المفتاح الخاص",
        "certFile" to "ملف الشهادة",
        "p12File" to "ملف P12",
        "jksFile" to "ملف JKS",
        "alias" to "الاسم المستعار للشهادة (Alias)",
        "pkcs11LibraryPath" to "مسار مكتبة PKCS#11",
        "pkcs11Slot" to "رقم فتحة PKCS#11",
        "showSignature" to "إظهار التوقيع في الصفحة",
        "reason" to "سبب التوقيع",
        "location" to "مكان التوقيع",
        "name" to "اسم الموقّع",
        "pageNumber" to "رقم صفحة التوقيع",
        "showLogo" to "إظهار شعار التوقيع"
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
