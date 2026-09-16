const LANGS = {
  en:{name:'영어',native:'English',tts:'en-US',speech:'en-US',api:'en',gas:'en',dir:'ltr'},
  zh:{name:'중국어(간체)',native:'中文',tts:'zh-CN',speech:'zh-CN',api:'zh',gas:'zh',dir:'ltr'},
  vi:{name:'베트남어',native:'Tiếng Việt',tts:'vi-VN',speech:'vi-VN',api:'vi',gas:'vi',dir:'ltr'},
  ru:{name:'러시아어',native:'Русский',tts:'ru-RU',speech:'ru-RU',api:'ru',gas:'ru',dir:'ltr'},
  uz:{name:'우즈베크어',native:"O‘zbekcha",tts:'uz-UZ',speech:'uz-UZ',api:null,gas:'uz',dir:'ltr'},
  mn:{name:'몽골어',native:'Монгол',tts:'mn-MN',speech:'mn-MN',api:null,gas:'mn',dir:'ltr'},
  ar:{name:'아랍어(표준)',native:'العربية',tts:'ar-EG',speech:'ar-SA',api:'ar',gas:'ar',dir:'rtl'},
  th:{name:'태국어',native:'ไทย',tts:'th-TH',speech:'th-TH',api:'th',gas:'th',dir:'ltr'},
  fil:{name:'필리핀어',native:'Filipino',tts:'fil-PH',speech:'fil-PH',api:null,gas:'tl',dir:'ltr'},
  esmx:{name:'스페인어(멕시코)',native:'Español (México)',tts:'es-MX',speech:'es-MX',api:'es',gas:'es',dir:'ltr'},
  kk:{name:'카자흐어',native:'Қазақша',tts:'kk-KZ',speech:'kk-KZ',api:null,gas:'kk',dir:'ltr'},
  ja:{name:'일본어',native:'日本語',tts:'ja-JP',speech:'ja-JP',api:'ja',gas:'ja',dir:'ltr'}
};

const SPEAK_PROMPT = {
  en:'Please speak.', zh:'请说。', vi:'Xin mời nói.', ru:'Говорите, пожалуйста.',
  uz:'Marhamat, gapiring.', mn:'Ярина уу.', ar:'تفضل، تحدث.', th:'เชิญพูดได้เลยค่ะ/ครับ',
  fil:'Maaari na po kayong magsalita.', esmx:'Puede hablar.', kk:'Айта беріңіз.', ja:'どうぞ、お話しください。'
};

const P = [
{id:'hello',cat:'인사',ko:'안녕하세요. 아산남성초등학교 행정실입니다. 무엇을 도와드릴까요?',t:{en:'Hello. This is the administrative office of Asan Namseong Elementary School. How can I help you?',zh:'您好。这里是牙山南城小学行政室。请问有什么可以帮您的吗？',vi:'Xin chào. Đây là văn phòng hành chính của Trường Tiểu học Asan Namseong. Tôi có thể giúp gì cho anh/chị?',ru:'Здравствуйте. Это административный офис начальной школы Асан Намсон. Чем я могу вам помочь?',uz:'Assalomu alaykum. Bu Asan Namseong boshlang‘ich maktabining ma’muriy bo‘limi. Sizga qanday yordam bera olaman?',mn:'Сайн байна уу. Энэ бол Асан Намсон бага сургуулийн захиргааны алба. Танд юугаар туслах вэ?',ar:'مرحبًا. هذا هو المكتب الإداري لمدرسة أسان نامسونغ الابتدائية. كيف يمكنني مساعدتك؟',th:'สวัสดีค่ะ/ครับ ที่นี่คือห้องธุรการโรงเรียนประถมอาซานนัมซอง มีอะไรให้ช่วยไหมคะ/ครับ',fil:'Magandang araw. Ito ang tanggapan ng administrasyon ng Asan Namseong Elementary School. Paano po namin kayo matutulungan?',esmx:'Hola. Esta es la oficina administrativa de la Escuela Primaria Asan Namseong. ¿En qué puedo ayudarle?',kk:'Сәлеметсіз бе. Бұл Асан Намсон бастауыш мектебінің әкімшілік бөлімі. Сізге қалай көмектесе аламын?',ja:'こんにちは。アサン・ナムソン小学校の事務室です。どのようなご用件でしょうか。'}},
{id:'goodbye',cat:'인사',ko:'업무 처리가 완료되었습니다. 방문해 주셔서 감사합니다. 조심히 돌아가세요.',t:{en:'Your request has been completed. Thank you for visiting. Please take care on your way home.',zh:'您的业务已经办理完毕。感谢您的来访，请慢走。',vi:'Công việc của anh/chị đã được xử lý xong. Cảm ơn anh/chị đã đến. Chúc anh/chị về nhà an toàn.',ru:'Ваш вопрос решён. Спасибо за визит. Счастливого пути домой.',uz:'Ishingiz yakunlandi. Tashrifingiz uchun rahmat. Uyga ehtiyot bo‘lib boring.',mn:'Таны ажил дууслаа. Хүрэлцэн ирсэнд баярлалаа. Аян замдаа сайн яваарай.',ar:'تم إنجاز معاملتك. شكرًا لزيارتك. نتمنى لك عودة آمنة إلى المنزل.',th:'ดำเนินการเรียบร้อยแล้ว ขอบคุณที่มาใช้บริการ เดินทางกลับโดยสวัสดิภาพค่ะ/ครับ',fil:'Tapos na po ang inyong transaksyon. Salamat po sa pagbisita. Ingat po sa pag-uwi.',esmx:'Su trámite ha sido completado. Gracias por su visita. Que tenga un buen regreso a casa.',kk:'Ісіңіз аяқталды. Келгеніңізге рақмет. Үйге аман-есен қайтыңыз.',ja:'お手続きが完了しました。ご来校いただきありがとうございます。お気をつけてお帰りください。'}},
{id:'wait',cat:'기본안내',ko:'잠시만 기다려 주세요. 학생 정보를 확인하겠습니다.',t:{en:"Please wait a moment. I’ll check the student’s information.",zh:'请稍等，我来确认学生信息。',vi:'Xin vui lòng đợi một chút. Tôi sẽ kiểm tra thông tin của học sinh.',ru:'Пожалуйста, немного подождите. Я проверю информацию об ученике.',uz:"Iltimos, biroz kuting. O‘quvchining ma’lumotlarini tekshiraman.",mn:'Түр хүлээнэ үү. Би сурагчийн мэдээллийг шалгая.',ar:'يرجى الانتظار قليلاً. سأتحقق من معلومات الطالب.',th:'กรุณารอสักครู่ ฉันจะตรวจสอบข้อมูลของนักเรียน',fil:'Mangyaring maghintay sandali. Titingnan ko ang impormasyon ng mag-aaral.',esmx:'Por favor, espere un momento. Voy a verificar la información del estudiante.',kk:'Сәл күте тұрыңыз. Оқушының ақпаратын тексеремін.',ja:'少々お待ちください。児童の情報を確認いたします。'}},
{id:'name',cat:'학생확인',ko:'학생 이름을 알려 주세요.',t:{en:'Please tell me the student’s name.',zh:'请告诉我学生的姓名。',vi:'Vui lòng cho tôi biết tên của học sinh.',ru:'Назовите, пожалуйста, имя ученика.',uz:"Iltimos, o‘quvchining ism-familiyasini ayting.",mn:'Сурагчийн нэрийг хэлнэ үү.',ar:'يرجى إخباري باسم الطالب.',th:'กรุณาแจ้งชื่อนักเรียน',fil:'Pakisabi ang pangalan ng mag-aaral.',esmx:'Por favor, dígame el nombre del estudiante.',kk:'Оқушының аты-жөнін айтыңыз.',ja:'児童のお名前を教えてください。'}},
{id:'grade',cat:'학생확인',ko:'학생의 학년과 반을 알려 주세요.',t:{en:'Please tell me the student’s grade and class.',zh:'请告诉我学生的年级和班级。',vi:'Vui lòng cho tôi biết khối và lớp của học sinh.',ru:'Назовите, пожалуйста, класс и номер класса ученика.',uz:"Iltimos, o‘quvchining sinfi va guruhini ayting.",mn:'Сурагчийн анги, бүлгийг хэлнэ үү.',ar:'يرجى إخباري بصف الطالب والشعبة.',th:'กรุณาแจ้งระดับชั้นและห้องของนักเรียน',fil:'Pakisabi ang baitang at seksyon ng mag-aaral.',esmx:'Por favor, dígame el grado y grupo del estudiante.',kk:'Оқушының сыныбы мен бөлімін айтыңыз.',ja:'児童の学年とクラスを教えてください。'}},
{id:'dob',cat:'학생확인',ko:'학생의 생년월일을 알려 주세요.',t:{en:'Please tell me the student’s date of birth.',zh:'请告诉我学生的出生日期。',vi:'Vui lòng cho tôi biết ngày sinh của học sinh.',ru:'Назовите, пожалуйста, дату рождения ученика.',uz:"Iltimos, o‘quvchining tug‘ilgan sanasini ayting.",mn:'Сурагчийн төрсөн огноог хэлнэ үү.',ar:'يرجى إخباري بتاريخ ميلاد الطالب.',th:'กรุณาแจ้งวันเดือนปีเกิดของนักเรียน',fil:'Pakisabi ang petsa ng kapanganakan ng mag-aaral.',esmx:'Por favor, dígame la fecha de nacimiento del estudiante.',kk:'Оқушының туған күнін айтыңыз.',ja:'児童の生年月日を教えてください。'}},
{id:'studentno',cat:'학생확인',ko:'학생 등록번호가 있으면 보여 주세요.',t:{en:'If you have the student registration number, please show it to me.',zh:'如果有学生登记号码，请出示。',vi:'Nếu có mã số đăng ký của học sinh, vui lòng cho tôi xem.',ru:'Если у вас есть регистрационный номер ученика, пожалуйста, покажите его.',uz:"Agar o‘quvchining ro‘yxatga olish raqami bo‘lsa, iltimos, ko‘rsating.",mn:'Сурагчийн бүртгэлийн дугаар байгаа бол үзүүлнэ үү.',ar:'إذا كان لديك رقم تسجيل الطالب، فيرجى إظهاره لي.',th:'หากมีเลขทะเบียนนักเรียน กรุณาแสดงให้ดู',fil:'Kung mayroon kayo ng numero ng rehistro ng mag-aaral, pakipakita ito.',esmx:'Si tiene el número de registro del estudiante, por favor muéstremelo.',kk:'Оқушының тіркеу нөмірі болса, көрсетіңіз.',ja:'児童の登録番号があれば、見せてください。'}},
{id:'id',cat:'학생확인',ko:'보호자 신분증을 보여 주세요.',t:{en:'Please show me the guardian’s identification.',zh:'请出示监护人的身份证件。',vi:'Vui lòng cho tôi xem giấy tờ tùy thân của người giám hộ.',ru:'Пожалуйста, покажите удостоверение личности родителя или опекуна.',uz:"Iltimos, ota-ona yoki vasiyning shaxsini tasdiqlovchi hujjatini ko‘rsating.",mn:'Эцэг эх эсвэл асран хамгаалагчийн үнэмлэхийг үзүүлнэ үү.',ar:'يرجى إظهار هوية ولي الأمر.',th:'กรุณาแสดงบัตรประจำตัวของผู้ปกครอง',fil:'Pakipakita ang ID ng magulang o tagapag-alaga.',esmx:'Por favor, muéstreme una identificación del padre, madre o tutor.',kk:'Ата-ананың немесе қамқоршының жеке куәлігін көрсетіңіз.',ja:'保護者の身分証明書を見せてください。'}},
{id:'passport',cat:'학생확인',ko:'외국인등록증이나 여권이 있으면 보여 주세요.',t:{en:'If you have an alien registration card or passport, please show it to me.',zh:'如果有外国人登录证或护照，请出示。',vi:'Nếu có thẻ đăng ký người nước ngoài hoặc hộ chiếu, vui lòng cho tôi xem.',ru:'Если у вас есть карта регистрации иностранца или паспорт, пожалуйста, покажите.',uz:"Agar chet elliklar ro‘yxat kartasi yoki pasport bo‘lsa, iltimos, ko‘rsating.",mn:'Гадаадын иргэний бүртгэлийн үнэмлэх эсвэл паспорт байгаа бол үзүүлнэ үү.',ar:'إذا كانت لديك بطاقة تسجيل أجنبي أو جواز سفر، فيرجى إظهاره.',th:'หากมีบัตรลงทะเบียนคนต่างด้าวหรือหนังสือเดินทาง กรุณาแสดงให้ดู',fil:'Kung mayroon kayong Alien Registration Card o pasaporte, pakipakita ito.',esmx:'Si tiene tarjeta de registro de extranjero o pasaporte, por favor muéstremelo.',kk:'Шетелдіктің тіркеу картасы немесе төлқұжатыңыз болса, көрсетіңіз.',ja:'外国人登録証またはパスポートがあれば、見せてください。'}},
{id:'doctype',cat:'증명서',ko:'어떤 서류가 필요하신가요?',t:{en:'What document do you need?',zh:'您需要什么文件？',vi:'Anh/chị cần loại giấy tờ nào?',ru:'Какой документ вам нужен?',uz:'Sizga qaysi hujjat kerak?',mn:'Танд ямар бичиг баримт хэрэгтэй вэ?',ar:'ما المستند الذي تحتاج إليه؟',th:'ต้องการเอกสารประเภทใดคะ/ครับ',fil:'Anong dokumento ang kailangan ninyo?',esmx:'¿Qué documento necesita?',kk:'Сізге қандай құжат қажет?',ja:'どの書類が必要ですか。'}},
{id:'enrollcert',cat:'증명서',ko:'재학증명서가 필요하신가요?',t:{en:'Do you need a certificate of enrollment?',zh:'您需要在学证明吗？',vi:'Anh/chị có cần giấy xác nhận đang theo học không?',ru:'Вам нужна справка об обучении в школе?',uz:"Sizga maktabda o‘qiyotganligi to‘g‘risida ma’lumotnoma kerakmi?",mn:'Сургуульд суралцаж буй тухай тодорхойлолт хэрэгтэй юу?',ar:'هل تحتاج إلى شهادة قيد دراسي؟',th:'ต้องการหนังสือรับรองการเป็นนักเรียนหรือไม่',fil:'Kailangan ninyo ba ng sertipiko na nagpapatunay na kasalukuyang nag-aaral ang estudyante?',esmx:'¿Necesita una constancia de estudios?',kk:'Оқушының осы мектепте оқитыны туралы анықтама керек пе?',ja:'在学証明書が必要ですか。'}},
{id:'fill',cat:'증명서',ko:'이 서류에 학생 이름과 보호자 연락처를 적어 주세요.',t:{en:'Please write the student’s name and the guardian’s contact number on this form.',zh:'请在这份表格上填写学生姓名和监护人的联系电话。',vi:'Vui lòng ghi tên học sinh và số điện thoại liên hệ của người giám hộ vào mẫu này.',ru:'Пожалуйста, укажите в этой форме имя ученика и контактный телефон родителя или опекуна.',uz:"Iltimos, ushbu blankaga o‘quvchining ismi va ota-ona yoki vasiyning telefon raqamini yozing.",mn:'Энэ маягтад сурагчийн нэр болон асран хамгаалагчийн холбоо барих дугаарыг бичнэ үү.',ar:'يرجى كتابة اسم الطالب ورقم هاتف ولي الأمر في هذه الاستمارة.',th:'กรุณากรอกชื่อนักเรียนและเบอร์ติดต่อของผู้ปกครองในแบบฟอร์มนี้',fil:'Pakisulat sa form na ito ang pangalan ng mag-aaral at contact number ng magulang o tagapag-alaga.',esmx:'Por favor, escriba en este formulario el nombre del estudiante y el número de contacto del padre, madre o tutor.',kk:'Осы бланкіге оқушының аты-жөнін және ата-ананың немесе қамқоршының байланыс нөмірін жазыңыз.',ja:'この書類に児童の名前と保護者の連絡先を記入してください。'}},
{id:'sign',cat:'증명서',ko:'여기에 서명해 주세요.',t:{en:'Please sign here.',zh:'请在这里签名。',vi:'Vui lòng ký tên ở đây.',ru:'Пожалуйста, подпишите здесь.',uz:'Iltimos, shu yerga imzo qo‘ying.',mn:'Энд гарын үсэг зурна уу.',ar:'يرجى التوقيع هنا.',th:'กรุณาลงลายมือชื่อที่นี่',fil:'Pakipirmahan dito.',esmx:'Por favor, firme aquí.',kk:'Осы жерге қол қойыңыз.',ja:'こちらに署名してください。'}},
{id:'complete',cat:'증명서',ko:'서류 발급이 완료되었습니다.',t:{en:'The document has been issued.',zh:'文件已办理完成。',vi:'Giấy tờ đã được cấp xong.',ru:'Документ готов и выдан.',uz:'Hujjat tayyor bo‘ldi.',mn:'Бичиг баримт бэлэн боллоо.',ar:'تم إصدار المستند.',th:'ออกเอกสารเรียบร้อยแล้ว',fil:'Naibigay na ang dokumento.',esmx:'El documento ya fue expedido.',kk:'Құжат дайын болды.',ja:'書類の発行が完了しました。'}},
{id:'fee',cat:'수납',ko:'수수료는 없습니다.',t:{en:'There is no fee.',zh:'不收取手续费。',vi:'Không có lệ phí.',ru:'Плата не взимается.',uz:'To‘lov olinmaydi.',mn:'Хураамжгүй.',ar:'لا توجد رسوم.',th:'ไม่มีค่าธรรมเนียม',fil:'Walang bayad.',esmx:'No hay ningún costo.',kk:'Төлем жоқ.',ja:'手数料はかかりません。'}},
{id:'payment',cat:'수납',ko:'학교에 납부해야 할 금액이 있습니다.',t:{en:'There is an amount that needs to be paid to the school.',zh:'有一笔费用需要缴纳给学校。',vi:'Có một khoản tiền cần nộp cho nhà trường.',ru:'Необходимо внести оплату в школу.',uz:'Maktabga to‘lanishi kerak bo‘lgan summa bor.',mn:'Сургуульд төлөх төлбөр байна.',ar:'هناك مبلغ يجب دفعه للمدرسة.',th:'มีจำนวนเงินที่ต้องชำระให้โรงเรียน',fil:'May halagang kailangang bayaran sa paaralan.',esmx:'Hay un monto que debe pagar a la escuela.',kk:'Мектепке төлеу қажет сома бар.',ja:'学校に納付していただく金額があります。'}},
{id:'transfer',cat:'전입·전출',ko:'전입·전출(전학) 관련 문의인가요?',t:{en:'Is your question about transferring into or out of this school?',zh:'您咨询的是转入或转出（转学）吗？',vi:'Anh/chị đang hỏi về việc chuyển đến hoặc chuyển khỏi trường phải không?',ru:'Ваш вопрос связан с переводом в эту школу или из неё?',uz:"Savolingiz maktabga ko‘chib kelish yoki boshqa maktabga o‘tish bilan bog‘liqmi?",mn:'Таны асуулт энэ сургуульд шилжин ирэх эсвэл өөр сургууль руу шилжихтэй холбоотой юу?',ar:'هل استفسارك يتعلق بالانتقال إلى هذه المدرسة أو منها؟',th:'สอบถามเกี่ยวกับการย้ายเข้า/ย้ายออกโรงเรียนใช่หรือไม่',fil:'Ang tanong ninyo ba ay tungkol sa paglipat papasok o palabas ng paaralang ito?',esmx:'¿Su consulta es sobre un traslado hacia esta escuela o a otra escuela?',kk:'Сұрағыңыз осы мектепке ауысу немесе басқа мектепке ауысу туралы ма?',ja:'転入・転出（転校）についてのお問い合わせですか。'}},
{id:'afterschool',cat:'방과후학교',ko:'방과후학교 신청·변경·취소 관련 문의인가요?',t:{en:'Is your question about applying for, changing, or canceling an after-school program?',zh:'您咨询的是课后课程的申请、变更或取消吗？',vi:'Anh/chị đang hỏi về việc đăng ký, thay đổi hoặc hủy chương trình sau giờ học phải không?',ru:'Ваш вопрос касается записи, изменения или отмены занятий после уроков?',uz:"Savolingiz maktabdan keyingi to‘garakka yozilish, o‘zgartirish yoki bekor qilish haqida mi?",mn:'Хичээлийн дараах хөтөлбөрт бүртгүүлэх, өөрчлөх эсвэл цуцлах тухай асуулт уу?',ar:'هل استفسارك يتعلق بالتسجيل في برنامج ما بعد المدرسة أو تغييره أو إلغائه؟',th:'สอบถามเกี่ยวกับการสมัคร เปลี่ยนแปลง หรือยกเลิกกิจกรรมหลังเลิกเรียนใช่หรือไม่',fil:'Ang tanong ninyo ba ay tungkol sa pag-apply, pagbabago, o pagkansela ng after-school program?',esmx:'¿Su consulta es sobre inscribirse, cambiar o cancelar un programa extracurricular?',kk:'Сұрағыңыз сабақтан кейінгі бағдарламаға жазылу, өзгерту немесе одан бас тарту туралы ма?',ja:'放課後学校プログラムの申請・変更・取消についてのお問い合わせですか。'}},
{id:'meal',cat:'급식',ko:'급식 관련 문의인가요?',t:{en:'Is your question about school meals?',zh:'您咨询的是学校供餐吗？',vi:'Anh/chị đang hỏi về bữa ăn ở trường phải không?',ru:'Ваш вопрос касается школьного питания?',uz:'Savolingiz maktab ovqatlanishi haqida mi?',mn:'Таны асуулт сургуулийн хоолтой холбоотой юу?',ar:'هل استفسارك يتعلق بالوجبات المدرسية؟',th:'สอบถามเกี่ยวกับอาหารกลางวันของโรงเรียนใช่หรือไม่',fil:'Ang tanong ninyo ba ay tungkol sa pagkain sa paaralan?',esmx:'¿Su consulta es sobre el servicio de alimentos de la escuela?',kk:'Сұрағыңыз мектептегі тамақтануға қатысты ма?',ja:'学校給食についてのお問い合わせですか。'}},
{id:'address',cat:'전입·전출',ko:'주소를 확인할 수 있는 서류가 필요합니다.',t:{en:'We need a document that can verify your address.',zh:'需要可以确认住址的文件。',vi:'Cần giấy tờ có thể xác nhận địa chỉ cư trú.',ru:'Нужен документ, подтверждающий адрес проживания.',uz:'Manzilni tasdiqlovchi hujjat kerak.',mn:'Оршин суугаа хаягийг батлах бичиг баримт хэрэгтэй.',ar:'نحتاج إلى مستند يثبت العنوان.',th:'จำเป็นต้องมีเอกสารที่ใช้ยืนยันที่อยู่',fil:'Kailangan namin ng dokumentong makapagpapatunay ng inyong tirahan.',esmx:'Necesitamos un documento que compruebe su domicilio.',kk:'Мекенжайыңызды растайтын құжат қажет.',ja:'住所を確認できる書類が必要です。'}},
{id:'relation',cat:'전입·전출',ko:'학생과 보호자의 관계를 확인할 수 있는 서류가 필요합니다.',t:{en:'We need a document that verifies the relationship between the student and the guardian.',zh:'需要可以证明学生与监护人关系的文件。',vi:'Cần giấy tờ xác nhận mối quan hệ giữa học sinh và người giám hộ.',ru:'Нужен документ, подтверждающий родство или отношения между учеником и опекуном.',uz:"O‘quvchi va ota-ona yoki vasiy o‘rtasidagi munosabatni tasdiqlovchi hujjat kerak.",mn:'Сурагч болон асран хамгаалагчийн хамаарлыг батлах бичиг баримт хэрэгтэй.',ar:'نحتاج إلى مستند يثبت العلاقة بين الطالب وولي الأمر.',th:'จำเป็นต้องมีเอกสารยืนยันความสัมพันธ์ระหว่างนักเรียนกับผู้ปกครอง',fil:'Kailangan namin ng dokumentong makapagpapatunay ng relasyon ng mag-aaral at ng magulang o tagapag-alaga.',esmx:'Necesitamos un documento que compruebe la relación entre el estudiante y el padre, madre o tutor.',kk:'Оқушы мен ата-ананың немесе қамқоршының арасындағы қатынасты растайтын құжат қажет.',ja:'児童と保護者の関係を確認できる書類が必要です。'}},
{id:'notunderstand',cat:'기본안내',ko:'이해가 안 되는 부분을 말씀해 주세요.',t:{en:'Please tell me which part you do not understand.',zh:'请告诉我您不明白的部分。',vi:'Vui lòng cho tôi biết phần nào anh/chị chưa hiểu.',ru:'Пожалуйста, скажите, какая часть вам непонятна.',uz:'Iltimos, tushunmagan joyingizni ayting.',mn:'Ойлгоогүй хэсгээ хэлнэ үү.',ar:'يرجى إخباري بالجزء الذي لم تفهمه.',th:'กรุณาบอกส่วนที่ไม่เข้าใจ',fil:'Pakisabi kung aling bahagi ang hindi ninyo naiintindihan.',esmx:'Por favor, dígame qué parte no entiende.',kk:'Түсінбеген жеріңізді айтыңыз.',ja:'分からない部分を教えてください。'}},
{id:'verify',cat:'기본안내',ko:'자동번역이므로 이름, 날짜, 금액 등 중요한 내용은 다시 확인해 주세요.',t:{en:'This is an automatic translation. Please double-check important details such as names, dates, and amounts.',zh:'这是自动翻译。姓名、日期、金额等重要信息请再次确认。',vi:'Đây là bản dịch tự động. Vui lòng kiểm tra lại các thông tin quan trọng như tên, ngày tháng và số tiền.',ru:'Это автоматический перевод. Пожалуйста, перепроверьте важные данные: имена, даты и суммы.',uz:"Bu avtomatik tarjima. Ism, sana va summa kabi muhim ma’lumotlarni qayta tekshiring.",mn:'Энэ нь автомат орчуулга. Нэр, огноо, мөнгөн дүн зэрэг чухал мэдээллийг дахин шалгана уу.',ar:'هذه ترجمة آلية. يرجى التحقق مرة أخرى من التفاصيل المهمة مثل الأسماء والتواريخ والمبالغ.',th:'นี่เป็นการแปลอัตโนมัติ กรุณาตรวจสอบข้อมูลสำคัญ เช่น ชื่อ วันที่ และจำนวนเงินอีกครั้ง',fil:'Awtomatikong salin ito. Pakisuri muli ang mahahalagang detalye tulad ng mga pangalan, petsa, at halaga.',esmx:'Esta es una traducción automática. Por favor, confirme nuevamente los datos importantes, como nombres, fechas y cantidades.',kk:'Бұл автоматты аударма. Аты-жөні, күндер және сомалар сияқты маңызды мәліметтерді қайта тексеріңіз.',ja:'自動翻訳ですので、氏名、日付、金額などの重要な内容はもう一度ご確認ください。'}}
];

const BUILTIN_LANG_KEYS = new Set(Object.keys(LANGS));
const CUSTOM_LANG_STORAGE = 'ans_custom_langs_v1';
const PRESET_CACHE_PREFIX = 'ans_preset_v1_';
const CUSTOM_PHRASE_STORAGE = 'ans_custom_phrases_v1';
const BUILTIN_PHRASE_OVERRIDE_STORAGE = 'ans_builtin_phrase_overrides_v1';
const SCHOOL_NAME_STORAGE = 'ans_school_name_v1';
const DEFAULT_SCHOOL_NAME = '아산남성초등학교';

// 기본문구 원본은 그대로 보존하고, 사용자가 수정한 내용만 별도로 저장합니다.
P.forEach(p=>{ if(!p.customPhrase && !p.baseKo) p.baseKo=p.ko; });
function getSchoolName(){
  const v=(localStorage.getItem(SCHOOL_NAME_STORAGE)||'').trim();
  return v||DEFAULT_SCHOOL_NAME;
}
function schoolDefaultText(p,schoolName=getSchoolName()){
  return String(p.baseKo||p.ko||'').split(DEFAULT_SCHOOL_NAME).join(schoolName);
}
function loadBuiltinPhraseOverrides(){
  let saved={};
  try{ saved=JSON.parse(localStorage.getItem(BUILTIN_PHRASE_OVERRIDE_STORAGE)||'{}')||{}; }catch{}
  const schoolName=getSchoolName();
  P.forEach(p=>{
    if(p.customPhrase)return;
    if(saved[p.id]&&typeof saved[p.id].ko==='string'){
      p.ko=String(saved[p.id].ko).trim();
      p.builtinEdited=true;
    }else{
      p.ko=schoolDefaultText(p,schoolName);
      p.builtinEdited=false;
    }
  });
}
function saveBuiltinPhraseOverrides(){
  const out={};
  P.forEach(p=>{
    if(!p.customPhrase && p.builtinEdited) out[p.id]={ko:p.ko,updatedAt:Date.now()};
  });
  localStorage.setItem(BUILTIN_PHRASE_OVERRIDE_STORAGE,JSON.stringify(out));
}
function phraseNeedsLiveTranslation(p){
  if(p.customPhrase||p.builtinEdited)return true;
  return !!(p.baseKo&&p.baseKo.includes(DEFAULT_SCHOOL_NAME)&&getSchoolName()!==DEFAULT_SCHOOL_NAME);
}
function applySchoolNameChange(oldName,newName){
  P.forEach(p=>{
    if(p.customPhrase)return;
    const before=p.ko;
    if(p.builtinEdited){
      if(oldName && p.ko.includes(oldName)) p.ko=p.ko.split(oldName).join(newName);
      else if(p.ko.includes(DEFAULT_SCHOOL_NAME)) p.ko=p.ko.split(DEFAULT_SCHOOL_NAME).join(newName);
    }else{
      p.ko=schoolDefaultText(p,newName);
    }
    if(before!==p.ko) clearPhraseTranslationCache(p.id);
  });
  saveBuiltinPhraseOverrides();
}

function loadCustomPhrases(){
  try{
    const arr=JSON.parse(localStorage.getItem(CUSTOM_PHRASE_STORAGE)||'[]');
    if(!Array.isArray(arr))return;
    const ids=new Set(P.map(x=>x.id));
    arr.forEach(x=>{
      if(!x||!x.id||!x.ko||ids.has(x.id))return;
      P.push({id:x.id,cat:x.cat||'기타',ko:String(x.ko).trim(),t:{},customPhrase:true,createdAt:x.createdAt||Date.now(),updatedAt:x.updatedAt||Date.now()});
      ids.add(x.id);
    });
  }catch(e){console.warn('custom phrase load',e);}
}
function saveCustomPhrases(){
  const arr=P.filter(x=>x.customPhrase).map(x=>({id:x.id,cat:x.cat,ko:x.ko,createdAt:x.createdAt||Date.now(),updatedAt:x.updatedAt||Date.now()}));
  localStorage.setItem(CUSTOM_PHRASE_STORAGE,JSON.stringify(arr));
}
function clearPhraseTranslationCache(id){
  try{
    const suffix='_'+id;
    Object.keys(localStorage).filter(k=>k.startsWith(PRESET_CACHE_PREFIX)&&k.endsWith(suffix)).forEach(k=>localStorage.removeItem(k));
  }catch(e){}
}
function makeCustomPhraseId(){return 'userp_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,6);}


function loadCustomLanguages(){
  try{
    const arr=JSON.parse(localStorage.getItem(CUSTOM_LANG_STORAGE)||'[]');
    if(!Array.isArray(arr))return;
    arr.forEach(x=>{
      if(!x||!x.key||!x.name||!x.gas||!x.speech)return;
      LANGS[x.key]={name:x.name,native:x.native||x.name,tts:x.tts||x.speech,speech:x.speech,api:x.api||x.gas,gas:x.gas,dir:x.dir==='rtl'?'rtl':'ltr',custom:true};
    });
  }catch(e){console.warn('custom language load',e);}
}
function saveCustomLanguages(){
  const arr=Object.entries(LANGS).filter(([k,v])=>v.custom).map(([key,v])=>({key,name:v.name,native:v.native,tts:v.tts,speech:v.speech,api:v.api,gas:v.gas,dir:v.dir}));
  localStorage.setItem(CUSTOM_LANG_STORAGE,JSON.stringify(arr));
}
loadCustomLanguages();
cleanupDuplicateCustomLanguages();
loadBuiltinPhraseOverrides();
loadCustomPhrases();

let currentLang = localStorage.getItem('ans_lang') || 'en';
if(!LANGS[currentLang]) currentLang='en';
let currentPhrase = P[0];
let currentCat = '전체';
let favorites = new Set(JSON.parse(localStorage.getItem('ans_favs') || '[]'));
let deferredPrompt = null;
const $ = s => document.querySelector(s);

function buildLanguages(){
  syncConversationLanguage();
  const bar=$('#langBar'); bar.innerHTML='';
  Object.entries(LANGS).forEach(([k,v])=>{
    const b=document.createElement('button');
    b.className='lang'+(k===currentLang?' active':'')+(v.custom?' custom':'');
    b.textContent=`${v.name} · ${v.native}`;
    b.title=v.custom?'사용자가 추가한 언어 · 선택 후 상단에서 삭제 가능':'';
    b.onclick=()=>{
      cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();window.speechSynthesis?.cancel();clearConversationDisplay();currentLang=k;localStorage.setItem('ans_lang',k);buildLanguages();renderPhrase();populateDynamic();updateSpeechLanguage();updateStaffLanguage();
    };
    bar.appendChild(b);
  });
  const del=$('#deleteSelectedLangBtn');
  if(del){
    const custom=!!LANGS[currentLang]?.custom;
    del.hidden=!custom;
    del.dataset.lang=custom?currentLang:'';
    del.title=custom?`${LANGS[currentLang].name} 삭제`:'기본 제공 언어는 삭제할 수 없습니다.';
  }
}
function buildFilters(){
  const cats=['전체','즐겨찾기','내 문구',...new Set(P.map(x=>x.cat))]; const el=$('#filters'); el.innerHTML='';
  cats.forEach(c=>{const b=document.createElement('button');b.className='filter'+(c===currentCat?' active':'');b.textContent=c;b.onclick=()=>{currentCat=c;buildFilters();renderList();};el.appendChild(b);});
}
function getPresetCache(p,langKey){
  try{return localStorage.getItem(PRESET_CACHE_PREFIX+langKey+'_'+p.id)||'';}catch{return '';}
}
function setPresetCache(p,langKey,text){
  try{localStorage.setItem(PRESET_CACHE_PREFIX+langKey+'_'+p.id,text);}catch{}
}
function knownPhraseText(p,langKey){return phraseNeedsLiveTranslation(p)?(getPresetCache(p,langKey)||''):(p.t?.[langKey]||getPresetCache(p,langKey)||'');}
async function ensurePhraseTranslation(p=currentPhrase,langKey=currentLang){
  const known=knownPhraseText(p,langKey);if(known)return known;
  const out=await translateText(p.ko,'ko',langKey);
  if(out)setPresetCache(p,langKey,out);
  return out;
}
async function phraseTextForAction(p=currentPhrase,langKey=currentLang){
  try{return await ensurePhraseTranslation(p,langKey);}
  catch(e){
    console.warn('preset translate',e);
    alert(e.message==='NO_SERVER'?'추가한 언어의 민원문장을 사용하려면 ⚙ 설정에서 번역 서버 연결 상태를 확인해 주세요.':'이 언어로 번역하지 못했습니다. 번역용 언어코드와 서버 연결 상태를 확인해 주세요.');
    return '';
  }
}
function visiblePhrases(){
  const q=$('#search').value.trim().toLowerCase();
  return P.filter(p=>{
    const foreign=knownPhraseText(p,currentLang).toLowerCase();
    const categoryOk=currentCat==='전체'||p.cat===currentCat||(currentCat==='즐겨찾기'&&favorites.has(p.id))||(currentCat==='내 문구'&&p.customPhrase);
    return categoryOk && (!q || p.ko.toLowerCase().includes(q) || foreign.includes(q) || p.cat.includes(q));
  });
}
function renderList(){
  const el=$('#phraseList');el.innerHTML='';
  const arr=visiblePhrases();
  if(!arr.length){el.innerHTML=currentCat==='내 문구'?'<div class="empty-state">아직 등록한 문구가 없습니다. 위 「＋ 내 문구 등록」을 눌러 추가하세요.</div>':'<div class="empty-state">검색 결과가 없습니다.</div>';return;}
  arr.forEach(p=>{
    const row=document.createElement('div');row.className='phrase'+(p.id===currentPhrase.id?' active':'')+(p.customPhrase?' custom-phrase':'');
    const customBadge=p.customPhrase?'<em class="custom-phrase-badge">내 문구</em>':'';
    const editBtn='<button class="phrase-mini phrase-edit" title="문구 수정" aria-label="문구 수정">✏ 수정</button>';
    const deleteBtn=p.customPhrase?'<button class="phrase-mini danger phrase-delete" title="문구 삭제" aria-label="문구 삭제">🗑</button>':'';
    row.innerHTML=`<div class="ptext"><b>${escapeHtml(p.ko)}</b><span>${escapeHtml(p.cat)} ${customBadge}</span></div><div class="phrase-actions"><button class="quick-speak" title="현재 선택 언어로 읽어주기" aria-label="${escapeHtml(p.ko)} 읽어주기">🔊<span>읽기</span></button>${editBtn}${deleteBtn}<button class="star ${favorites.has(p.id)?'on':''}" title="즐겨찾기" aria-label="즐겨찾기">${favorites.has(p.id)?'★':'☆'}</button></div>`;
    row.querySelector('.ptext').onclick=()=>{setMainTab('preset');currentPhrase=p;renderPhrase();};
    row.querySelector('.quick-speak').onclick=async e=>{e.stopPropagation();setMainTab('preset');currentPhrase=p;renderPhrase();const tx=await phraseTextForAction(p,currentLang);if(tx)speakPhraseWithAutoFollow(tx,currentLang);};
    row.querySelector('.star').onclick=e=>{e.stopPropagation();toggleFav(p.id);};
    const edit=row.querySelector('.phrase-edit');if(edit)edit.onclick=e=>{e.stopPropagation();p.customPhrase?openCustomPhraseManager(p.id):openBuiltinPhraseEditor(p.id);};
    const del=row.querySelector('.phrase-delete');if(del)del.onclick=e=>{e.stopPropagation();deleteCustomPhrase(p.id);};
    el.appendChild(row);
  });
}
function renderPhrase(){
  const L=LANGS[currentLang];
  $('#koText').textContent=currentPhrase.ko;
  $('#langLabel').textContent=`${L.name} · ${L.native}${L.custom?' · 추가 언어':''}`;
  const t=$('#translated'); t.dir=L.dir;
  const known=knownPhraseText(currentPhrase,currentLang);
  if(known){t.textContent=known;}
  else{
    t.textContent=getServerUrl()?'번역 준비 중…':'추가 언어입니다. 자유대화 번역 서버를 연결하면 이 문장도 자동 번역됩니다.';
    const phraseId=currentPhrase.id,langKey=currentLang;
    ensurePhraseTranslation(currentPhrase,currentLang).then(out=>{
      if(currentPhrase.id===phraseId && currentLang===langKey){t.textContent=out;t.dir=LANGS[langKey].dir;}
    }).catch(()=>{
      if(currentPhrase.id===phraseId && currentLang===langKey)t.textContent='번역 서버 연결 또는 언어코드를 확인해 주세요.';
    });
  }
  $('#favBtn').textContent=favorites.has(currentPhrase.id)?'★ 즐겨찾기 해제':'☆ 즐겨찾기';
  const isCustom=!!currentPhrase.customPhrase;
  $('#editPhraseBtn').hidden=false;$('#deletePhraseBtn').hidden=!isCustom;
  renderList();
}
function saveFavoriteIds(){localStorage.setItem('ans_favs',JSON.stringify([...favorites]));}
function toggleFav(id){favorites.has(id)?favorites.delete(id):favorites.add(id);saveFavoriteIds();renderList();renderPhrase();}
function speak(text,langKey){cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();if(!('speechSynthesis' in window)){alert('이 기기에서는 음성 읽기를 지원하지 않습니다.');return;}speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang=LANGS[langKey]?.tts||langKey;u.rate=.92;speechSynthesis.speak(u);}
async function copyText(text){try{await navigator.clipboard.writeText(text);}catch{const ta=document.createElement('textarea');ta.value=text;document.body.appendChild(ta);ta.select();document.execCommand('copy');ta.remove();}}
function escapeHtml(s){return String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}
async function openBig(){
  const L=LANGS[currentLang];const tx=await phraseTextForAction(currentPhrase,currentLang);if(!tx)return;
  $('#modalLang').textContent=`${L.name} · ${L.native}`;$('#modalText').textContent=tx;$('#modalText').dir=L.dir;$('#bigModal').classList.add('show');
}
function closeBig(){$('#bigModal').classList.remove('show');}

function populateDynamic(){
  const src=$('#sourceLang'), tar=$('#targetLang'); src.innerHTML='';tar.innerHTML='';
  const koOpt='<option value="ko">한국어</option>'; src.insertAdjacentHTML('beforeend',koOpt);tar.insertAdjacentHTML('beforeend',koOpt);
  Object.entries(LANGS).forEach(([k,v])=>{src.insertAdjacentHTML('beforeend',`<option value="${k}">${v.name}</option>`);tar.insertAdjacentHTML('beforeend',`<option value="${k}">${v.name}</option>`);});
  src.value='ko';tar.value=currentLang;checkTranslatorStatus();
}
function apiCode(k){if(k==='ko')return 'ko'; return LANGS[k]?.api||null;}
function gasCode(k){if(k==='ko')return 'ko'; return LANGS[k]?.gas||null;}
const DEFAULT_SERVER_URL='https://script.google.com/macros/s/AKfycbzXS4sGTdMz0ZXCJx0fEYAs7xLcZi-iT-dYEERbi4it2N5j4ZLR6JAIU0_dwdM9ZeKT/exec';
function getSavedServerUrl(){return (localStorage.getItem('ans_gas_url')||'').trim();}
function getServerUrl(){const saved=getSavedServerUrl();return isValidServerUrl(saved)?saved:DEFAULT_SERVER_URL;}

function isValidServerUrl(u){return /^https:\/\/script\.google\.com\/macros\/s\/.+\/exec(?:\?.*)?$/.test((u||'').trim());}
function syncServerInputs(){
  const u=getServerUrl();
  const q=$('#quickServerUrlInput'),m=$('#serverUrlInput');
  if(q && document.activeElement!==q) q.value=u;
  if(m && document.activeElement!==m) m.value=u;
}
function setQuickServerBadge(mode,text){
  const b=$('#quickServerBadge');
  if(b){b.className='server-badge'+(mode?' '+mode:''); b.textContent=text;}
  const h=$('#headerServerBadge');
  if(h){h.className='header-server-badge'+(mode?' '+mode:''); h.textContent='● '+(text==='연결됨'?'통역 준비됨':text==='확인 중'?'통역 연결 확인 중':'통역 연결 확인 필요');}
}
async function testServerEverywhere(){
  syncServerInputs();
  if(!getServerUrl()){
    setQuickServerBadge('','미설정');
    const q=$('#quickServerStatus'); if(q){q.className='status warn';q.textContent='기본 번역 서버 연결을 확인해 주세요.';}
    return false;
  }
  setQuickServerBadge('checking','확인 중');
  const ok=await testServer('#quickServerStatus');
  setQuickServerBadge(ok?'ok':'',ok?'연결됨':'연결 실패');
  return ok;
}
async function saveServerFromInput(inputSelector,statusSelector){
  const input=$(inputSelector), status=$(statusSelector);
  const u=(input?.value||'').trim();
  if(!u || !isValidServerUrl(u)){
    if(status){status.className='status warn';status.textContent='Google Apps Script 웹앱의 /exec 주소를 정확히 입력해 주세요.';}
    setQuickServerBadge('','미설정');
    return false;
  }
  localStorage.setItem('ans_gas_url',u);
  syncServerInputs();
  const ok=await testServerEverywhere();
  checkTranslatorStatus();
  if(statusSelector!=='#quickServerStatus') await testServer(statusSelector);
  return ok;
}
function syncDynamicTarget(){if($('#sourceLang').value==='ko'){$('#targetLang').value=currentLang;checkTranslatorStatus();}}
function jsonpRequest(params, timeoutMs=12000){
  const base=getServerUrl();
  if(!base) return Promise.reject(new Error('NO_SERVER'));
  return new Promise((resolve,reject)=>{
    const cb='ANS_CB_'+Date.now()+'_'+Math.random().toString(36).slice(2);
    const script=document.createElement('script');
    const timer=setTimeout(()=>cleanup(new Error('TIMEOUT')),timeoutMs);
    function cleanup(err,data){clearTimeout(timer);try{delete window[cb]}catch{};script.remove();err?reject(err):resolve(data);}
    window[cb]=(data)=>cleanup(null,data);
    const q=new URLSearchParams({...params,callback:cb,_:Date.now().toString()});
    script.src=base+(base.includes('?')?'&':'?')+q.toString();
    script.onerror=()=>cleanup(new Error('NETWORK'));
    document.head.appendChild(script);
  });
}
async function testServer(showTarget='#serverStatus'){
  const el=$(showTarget);
  if(!getServerUrl()){el.className='status warn';el.textContent='기본 번역 서버 주소를 불러오지 못했습니다. ⚙ 설정의 고급 연결 설정을 확인해 주세요.';return false;}
  el.className='status warn';el.textContent='연결을 확인하고 있습니다…';
  try{
    const data=await jsonpRequest({ping:'1'},8000);
    if(data?.ok){
      if(data.version && data.version!=='6.0'){el.className='status warn';el.textContent=`✓ 서버 연결됨 · 현재 ${data.version}. 일본어 자유대화/직접 추가 언어를 쓰려면 ZIP의 Code.gs v6.0으로 업데이트 후 새 버전 배포가 필요합니다.`;return true;}
      el.className='status ok';el.textContent='✓ 무료 번역 서버 연결 정상 · v6.0 추가 언어 지원';return true;
    }
    throw new Error(data?.error||'BAD_RESPONSE');
  }
  catch(e){el.className='status warn';el.textContent='연결 실패 · Apps Script 웹앱 배포 주소와 접근 권한을 확인해 주세요.';return false;}
}
async function checkTranslatorStatus(){
  const el=$('#translatorStatus'), s=apiCode($('#sourceLang').value), t=apiCode($('#targetLang').value);
  if($('#sourceLang').value===$('#targetLang').value){el.className='status warn';el.textContent='입력 언어와 번역 언어를 다르게 선택해 주세요.';return;}
  if(getServerUrl()){el.className='status';el.textContent='번역 준비 · 연결 상태는 설정에서 확인할 수 있습니다.';return;}
  if(s&&t&&('Translator' in self)){
    try{const a=await Translator.availability({sourceLanguage:s,targetLanguage:t});if(['available','downloadable','downloading'].includes(a)){el.className='status ok';el.textContent='이 브라우저에서는 기기 내 번역을 사용할 수 있습니다.';return;}}catch(e){}
  }
  el.className='status warn';el.textContent='자주 쓰는 내장 문장은 바로 사용할 수 있습니다. 자유대화 번역에 문제가 있으면 ⚙ 설정에서 연결 상태를 확인해 주세요.';
}
function translationChunks(text){
  const chunks=[];let rest=text;
  while(rest.length>180){
    let cut=180;
    if(/[\uD800-\uDBFF]/.test(rest[cut-1]))cut--;
    const prefix=rest.slice(0,cut), matches=[...prefix.matchAll(/[.!?。！？\n]\s*|\s+/g)];
    const boundary=matches.length ? matches[matches.length-1].index+matches[matches.length-1][0].length : 0;
    if(boundary>=80)cut=boundary;
    chunks.push(rest.slice(0,cut));rest=rest.slice(cut);
  }
  if(rest)chunks.push(rest);
  return chunks;
}
async function translateViaAppsScript(text, sourceKey, targetKey){
  const src=gasCode(sourceKey),dst=gasCode(targetKey);
  if(!src||!dst)throw new Error('UNSUPPORTED_LANG');
  if(text.length>3600)throw new Error('TOO_LONG');
  const chunks=translationChunks(text), output=new Array(chunks.length);
  let next=0;
  async function worker(){
    while(next<chunks.length){
      const index=next++;
      const data=await jsonpRequest({q:chunks[index],source:src,target:dst},15000);
      if(!data?.ok)throw new Error(data?.error||'TRANSLATE_FAILED');
      output[index]=data.text||'';
    }
  }
  await Promise.all(Array.from({length:Math.min(2,chunks.length)},worker));
  return output.join(' ');
}
function withDeadline(promise, ms) {
  let timer;
  return Promise.race([promise, new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('TIMEOUT')),ms);})]).finally(()=>clearTimeout(timer));
}
async function translateText(text, sourceKey, targetKey){
  text=(text||'').trim();
  if(!text)throw new Error('EMPTY');
  if(sourceKey===targetKey)return text;
  const s=apiCode(sourceKey),t=apiCode(targetKey);
  if(s&&t&&('Translator' in self)){
    let tr;
    try{
      const avail=await withDeadline(Translator.availability({sourceLanguage:s,targetLanguage:t}),250);
      if(avail==='available'){
        let expired=false;
        const create=Translator.create({sourceLanguage:s,targetLanguage:t});
        create.then(value=>{if(expired)value.destroy?.();},()=>{});
        try{tr=await withDeadline(create,600);}catch(e){expired=true;throw e;}
        return await withDeadline(tr.translate(text),1800);
      }
    }catch(e){console.warn('local translator fallback',e.message);}
    finally{tr?.destroy?.();}
  }
  return await translateViaAppsScript(text,sourceKey,targetKey);
}
async function translateAnyToKorean(text, sourceKey){return translateText(text,sourceKey,'ko');}

let recognition=null;
let isListening=false;
let isPrompting=false;
const SPEECH_SILENCE_MS=3000;
let visitorSpeechTimer=null;
let visitorLatestText='';
let visitorSessionId=0;
function updateSpeechLanguage(){
  const L=LANGS[currentLang];
  const n=$('#speechLangName'); if(n)n.textContent=`${L.name} · ${L.native}`;
  const m=$('#manualForeignText'); if(m){m.dir=L.dir;m.placeholder=`${L.name}로 인식된 문장을 수정하거나 직접 입력하세요.`;}
  const ps=$('#speechPackStatus'); if(ps){ps.className='status';ps.textContent=`${L.name} 음성 언어팩 상태를 확인하려면 위 버튼을 누르세요.`;}
}
function getRecognitionCtor(){return window.SpeechRecognition||window.webkitSpeechRecognition||null;}

async function ensureMicrophonePermission(statusEl){
  const status = typeof statusEl==='string' ? $(statusEl) : statusEl;
  const explain = '지폴드7: 설정 → 애플리케이션 → Chrome(또는 남성초 통역기) → 권한 → 마이크 → 앱 사용 중에만 허용. 그다음 Chrome에서 이 사이트를 열고 주소창의 사이트 정보 → 권한 → 마이크 → 허용으로 설정해 주세요.';
  if(!window.isSecureContext){
    if(status){status.className='status warn';status.textContent='마이크는 HTTPS 보안 연결에서만 사용할 수 있습니다.';}
    return false;
  }
  if(!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia){
    if(status){status.className='status warn';status.textContent='이 브라우저에서는 마이크 권한 요청 기능을 사용할 수 없습니다. '+explain;}
    return false;
  }
  try{
    if(status){status.className='status warn';status.textContent='마이크 권한을 확인하고 있습니다… 권한 창이 뜨면 「허용」을 눌러 주세요.';}
    // Android/PWA에서는 Permissions API 상태가 실제 getUserMedia 동작과 다르게 보일 수 있어
    // 권한 상태만으로 중단하지 않고 실제 마이크 요청 결과를 기준으로 판단한다.
    const stream=await navigator.mediaDevices.getUserMedia({audio:true});
    stream.getTracks().forEach(t=>t.stop());
    if(status){status.className='status ok';status.textContent='✓ 마이크 권한이 허용되었습니다. 이제 음성통역 버튼을 눌러 말씀해 주세요.';}
    return true;
  }catch(e){
    console.warn('microphone permission',e);
    if(status){
      status.className='status warn';
      if(e?.name==='NotAllowedError' || e?.name==='SecurityError') status.textContent='마이크 사용이 차단되어 있습니다. '+explain;
      else if(e?.name==='NotFoundError') status.textContent='사용할 수 있는 마이크를 찾지 못했습니다. 스마트폰의 마이크 사용 제한을 확인해 주세요.';
      else status.textContent='마이크를 열지 못했습니다. '+explain;
    }
    return false;
  }
}

async function runMicDiagnostic(targetEl){
  const box=typeof targetEl==='string'?$(targetEl):targetEl;
  if(!box)return;
  box.hidden=false;box.className='diagbox';
  const rows=[];
  const add=(k,v)=>rows.push(`${k}: ${v}`);
  add('주소', location.origin+location.pathname);
  add('HTTPS 보안연결', window.isSecureContext?'정상':'아님');
  add('설치형 앱', (matchMedia?.('(display-mode: standalone)')?.matches||navigator.standalone)?'예':'아니오/브라우저');
  let policy='확인 불가';
  try{
    const pp=document.permissionsPolicy||document.featurePolicy;
    if(pp?.allowsFeature) policy=pp.allowsFeature('microphone')?'허용':'차단';
  }catch(e){policy='확인 중 오류';}
  add('페이지 Permissions-Policy microphone',policy);
  add('getUserMedia 지원', navigator.mediaDevices?.getUserMedia?'예':'아니오');
  let perm='확인 불가';
  try{ if(navigator.permissions?.query){perm=(await navigator.permissions.query({name:'microphone'})).state;} }catch(e){perm='이 브라우저에서 조회 불가';}
  add('브라우저 마이크 권한 상태',perm);
  const Ctor=getRecognitionCtor();
  add('Web Speech 음성인식',Ctor?'지원':'미지원');
  box.textContent=rows.join('\n')+'\n\n실제 마이크를 여는 중…';
  if(!navigator.mediaDevices?.getUserMedia){box.classList.add('bad');return;}
  try{
    const stream=await navigator.mediaDevices.getUserMedia({audio:true});
    const tracks=stream.getAudioTracks();
    const label=tracks[0]?.label||'마이크 장치';
    tracks.forEach(t=>t.stop());
    add('실제 마이크 열기','성공 ('+label+')');
    box.classList.add('ok');
    box.textContent=rows.join('\n')+'\n\n✓ 마이크 자체는 정상입니다. 여기까지 성공하고 음성인식만 실패하면 Chrome 음성인식 서비스 문제입니다.';
  }catch(e){
    add('실제 마이크 열기',`실패 · ${e?.name||'Error'} · ${e?.message||''}`);
    box.classList.add('bad');
    box.textContent=rows.join('\n')+'\n\n✕ 위 오류명으로 원인을 구분할 수 있습니다. 이 화면을 캡처해 보내주세요.';
  }
}

async function manageSpeechPack(installIfNeeded=true){
  const Ctor=getRecognitionCtor(), status=$('#speechPackStatus'), btn=$('#speechPackBtn');
  const L=LANGS[currentLang];
  if(!Ctor){status.className='status warn';status.textContent='이 브라우저는 음성 인식을 지원하지 않습니다.';return false;}
  if(typeof Ctor.available!=='function' || typeof Ctor.install!=='function'){
    status.className='status warn';
    status.textContent='현재 브라우저는 앱 안에서 음성 언어팩 설치 기능을 지원하지 않습니다. 브라우저/운영체제를 최신 버전으로 업데이트한 뒤 다시 확인해 주세요. 온라인 음성 인식은 계속 사용할 수 있습니다.';
    return false;
  }
  try{
    btn.disabled=true;status.className='status warn';status.textContent=`${L.name} 음성 언어팩 상태를 확인하고 있습니다…`;
    const a=await Ctor.available({langs:[L.speech],processLocally:true});
    if(a==='available'){status.className='status ok';status.textContent=`✓ ${L.name} 기기 내 음성 언어팩이 설치되어 있습니다. 민원인 음성을 기기에서 인식할 수 있습니다.`;return true;}
    if(a==='unavailable'){status.className='status warn';status.textContent=`현재 이 기기에서는 ${L.name} 기기 내 음성 언어팩을 제공하지 않습니다. 온라인 음성 인식을 사용합니다.`;return false;}
    if(!installIfNeeded){status.className='status warn';status.textContent=`${L.name} 음성 언어팩을 다운로드할 수 있습니다.`;return false;}
    status.className='status warn';status.textContent=`${L.name} 음성 언어팩을 다운로드하고 있습니다…`;
    const ok=await Ctor.install({langs:[L.speech],processLocally:true});
    if(ok){status.className='status ok';status.textContent=`✓ ${L.name} 음성 언어팩 설치가 완료되었습니다.`;return true;}
    status.className='status warn';status.textContent=`${L.name} 음성 언어팩 설치에 실패했습니다. 네트워크 연결과 브라우저 지원 상태를 확인해 주세요.`;return false;
  }catch(e){console.warn('speech pack',e);status.className='status warn';status.textContent='언어팩 상태를 확인하거나 설치하지 못했습니다. 이 기능은 브라우저별로 지원 여부가 다릅니다.';return false;}
  finally{btn.disabled=false;}
}
function setVisitorMicIdle(){
  isListening=false;isPrompting=false;
  const b=$('#micBtn');if(b){b.classList.remove('listening');b.disabled=false;b.textContent='말씀하세요';}
}
function cancelVisitorPendingTranslation(clearText=false){
  if(visitorSpeechTimer){clearTimeout(visitorSpeechTimer);visitorSpeechTimer=null;}
  visitorSessionId++;
  if(clearText)visitorLatestText='';
}
function stopListening(cancelPending=false){
  if(activeSpeech?.side==='visitor')cancelActiveSpeech();
  try{recognition&&recognition.stop();}catch{}
  recognition=null;
  setVisitorMicIdle();
  if(cancelPending)cancelVisitorPendingTranslation(false);
}
function speakKorean(text){
  cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();
  if(!text)return;
  if(!('speechSynthesis' in window)){alert('이 기기에서는 음성 읽기를 지원하지 않습니다.');return;}
  speechSynthesis.cancel();
  const u=new SpeechSynthesisUtterance(text);u.lang='ko-KR';u.rate=.95;speechSynthesis.speak(u);
}
async function translateSpeechText(text,opts={}){return translateConversation('visitor',text,opts);}

async function startListening(){return beginSpeech('visitor');}


let staffRecognition=null;
let staffListening=false;
let staffSpeechTimer=null;
let staffLatestText='';
let staffSessionId=0;
function updateStaffLanguage(){
  const L=LANGS[currentLang];
  const target=$('#staffTargetLabel'); if(target)target.textContent=L.name;
  const label=$('#staffForeignLabel'); if(label)label.textContent=`민원인에게 보여줄 ${L.name} 번역`;
  const result=$('#staffForeignResult'); if(result)result.dir=L.dir;
}
function setStaffMicIdle(){
  staffListening=false;
  const b=$('#staffMicBtn');if(b){b.classList.remove('listening');b.disabled=false;b.textContent='내가 말하기';}
}
function cancelStaffPendingTranslation(clearText=false){
  if(staffSpeechTimer){clearTimeout(staffSpeechTimer);staffSpeechTimer=null;}
  staffSessionId++;
  if(clearText)staffLatestText='';
}
function stopStaffListening(cancelPending=false){
  if(activeSpeech?.side==='staff')cancelActiveSpeech();
  try{staffRecognition&&staffRecognition.stop();}catch{}
  staffRecognition=null;setStaffMicIdle();
  if(cancelPending)cancelStaffPendingTranslation(false);
}
async function translateStaffText(text,opts={}){return translateConversation('staff',text,opts);}
async function translateConversation(side,text,opts={}){
  showConversationSide(side);
  $(side==='staff'?'#staffForeignResult':'#koreanResult').dataset.ready='false';
  text=(text||'').trim();if(!text)return;
  const staff=side==='staff';
  if(opts.autoSpeak && SpeechSession.needsReview([{text,final:true}])){
    const status=$(staff?'#staffSpeechStatus':'#speechStatus');
    status.className='status warn';
    $(staff?'#staffEditor':'#visitorEditor').open=true;
    status.textContent='반복된 말이 감지되어 자동번역을 멈췄습니다. 아래 문장을 수정한 뒤 번역을 눌러 주세요.';
    $(staff?'#staffForeignResult':'#koreanResult').textContent='문장 확인 후 번역합니다.';
    return;
  }
  if(opts.sessionId==null){cancelActiveSpeech();window.speechSynthesis?.cancel();}
  const session=staff?++staffSessionId:++visitorSessionId;
  const lang=opts.langKey||currentLang;
  const valid=()=>session===(staff?staffSessionId:visitorSessionId)&&lang===currentLang;
  const status=$(staff?'#staffSpeechStatus':'#speechStatus');
  const result=$(staff?'#staffForeignResult':'#koreanResult');
  const start=performance.now();
  const update=()=>{if(valid()){status.className='status warn';status.textContent=`번역 중 · ${((performance.now()-start)/1000).toFixed(1)}초`;}};
  update();result.textContent='번역 중…';const timer=setInterval(update,250);
  try{
    const out=await translateText(text,staff?'ko':lang,staff?lang:'ko');
    if(!valid())return;
    clearInterval(timer);result.dataset.ready='true';result.textContent=out;result.dir=staff?LANGS[lang].dir:'ltr';
    const elapsed=((performance.now()-start)/1000).toFixed(1);
    let flowCompleted=false;
    const afterComplete=()=>{if(flowCompleted||!valid())return;flowCompleted=true;try{opts.afterComplete?.();}catch(e){console.warn('conversation flow',e);}};
    const complete=()=>{if(valid()){status.className='status ok';status.textContent=`번역 완료 · 번역에 ${elapsed}초 소요 · 이름·날짜·금액은 확인해 주세요.`;}};
    complete();
    if(opts.autoSpeak&&$(staff?'#staffAutoSpeak':'#visitorAutoSpeak').checked&&'speechSynthesis' in window){
      speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(out);
      u.lang=staff?LANGS[lang].tts:'ko-KR';u.rate=.92;
      u.onstart=()=>{if(valid())status.textContent='읽는 중 · 번역에 '+elapsed+'초 소요';};
      u.onend=()=>{complete();afterComplete();};
      u.onerror=()=>{if(valid()){status.className='status warn';status.textContent='번역은 완료했습니다. 읽기 버튼을 눌러 재생해 주세요.';}afterComplete();};
      speechSynthesis.speak(u);
    }else{
      setTimeout(afterComplete,250);
    }
  }catch(e){
    if(!valid())return;
    result.textContent='번역하지 못했습니다.';status.className='status warn';
    status.textContent=e.message==='TOO_LONG'?'내용이 너무 깁니다. 3,600자 이내로 나누어 주세요.':'번역 연결을 확인해 주세요. 입력한 문장은 남아 있으니 다시 번역할 수 있습니다.';
    if(opts.autoFlow)pauseAutoConversation('번역 오류로 자동대화를 일시정지했습니다.');
  }finally{clearInterval(timer);}
}

async function startStaffListening(){return beginSpeech('staff');}

let activeSpeech=null;
let speechStartToken=0;
function cancelActiveSpeech(){
  speechStartToken++;
  activeSpeech?.cancel();activeSpeech=null;
  setVisitorMicIdle();setStaffMicIdle();
}
async function beginSpeech(side){
  showConversationSide(side);
  const staff=side==='staff', status=$(staff?'#staffSpeechStatus':'#speechStatus');
  if(activeSpeech?.side===side){
    cancelActiveSpeech();status.className='status';status.textContent='중지했습니다 · 다시 말하려면 버튼을 누르세요.';return;
  }
  cancelActiveSpeech();
  cancelStaffPendingTranslation(true);cancelVisitorPendingTranslation(true);
  window.speechSynthesis?.cancel();
  const token=speechStartToken, Ctor=getRecognitionCtor();
  if(!Ctor){status.textContent='이 브라우저에서는 음성인식을 사용할 수 없습니다. 아래에 직접 입력해 주세요.';if(autoConversationActive)pauseAutoConversation('음성인식을 사용할 수 없어 자동대화를 일시정지했습니다.');return;}
  const button=$(staff?'#staffMicBtn':'#micBtn');button.disabled=true;
  if(!(await ensureMicrophonePermission(status))||token!==speechStartToken){if(token===speechStartToken)button.disabled=false;if(autoConversationActive)pauseAutoConversation('마이크 권한을 확인해 주세요. 자동대화를 일시정지했습니다.');return;}
  const lang=currentLang, sessionId=staff?staffSessionId:visitorSessionId;
  const input=$(staff?'#manualKoreanText':'#manualForeignText');
  const heard=$(staff?'#staffHeardText':'#heardText');
  $(staff?'#staffEditor':'#visitorEditor').open=false;
  $(staff?'#staffForeignResult':'#koreanResult').dataset.ready='false';
  input.value='';heard.textContent='듣는 중 · 문장이 확정되면 표시합니다.';
  $(staff?'#staffForeignResult':'#koreanResult').textContent='새 말씀을 듣고 있습니다.';
  const valid=()=>token===speechStartToken;
  const idle=()=>{if(valid()){activeSpeech=null;staff?setStaffMicIdle():setVisitorMicIdle();}};
  const controller=new SpeechSession({
    finalOnly:/Android/i.test(navigator.userAgent),
    state(phase,seconds){
      if(!valid())return;
      button.disabled=false;button.classList.add('listening');button.textContent='■ 듣는 중 · 중지';
      status.className='status ok';
      status.textContent=phase==='waiting'?`말씀을 멈추셨나요? ${seconds}초 후 번역 · 이어서 말하면 다시 기다립니다.`:phase==='finalizing'?'인식한 문장을 확인하고 있습니다…':'듣는 중 · 휴대폰을 가까이 두고 한 문장씩 말씀하세요.';
    },
    text(text){if(!valid())return;heard.textContent=text;input.value=text;if(staff)staffLatestText=text;else visitorLatestText=text;},
    done(text){
      if(!valid())return;idle();heard.textContent=text;input.value=text;
      const autoFlow=conversationMode==='auto'&&autoConversationActive;
      (staff?translateStaffText:translateSpeechText)(text,{autoSpeak:true,sessionId,langKey:lang,autoFlow,afterComplete:autoFlow?()=>scheduleAutoTurn(staff?'visitor':'staff'):null});
    },
    review(text,reason){
      if(!valid())return;idle();input.value=text;
      $(staff?'#staffEditor':'#visitorEditor').open=true;
      $(staff?'#staffForeignResult':'#koreanResult').textContent='문장 확인 후 번역합니다.';
      status.className='status warn';status.textContent=reason==='repetition'?'같은 말이 반복 인식된 것 같습니다. 아래 문장을 확인·수정한 뒤 번역을 눌러 주세요.':'음성이 확정되지 않았습니다. 아래 문장을 확인·수정한 뒤 번역을 눌러 주세요.';
      if(autoConversationActive)pauseAutoConversation('인식 문장 확인이 필요해 자동대화를 일시정지했습니다.');
    },
    error(error){
      if(!valid())return;idle();status.className='status warn';
      status.textContent=error==='not-allowed'?'마이크 권한이 차단되었습니다. 설정의 마이크 진단을 확인해 주세요.':error==='language-not-supported'?'선택 언어의 음성인식을 지원하지 않습니다. 직접 입력해 주세요.':error==='network'?'음성인식 연결이 끊겼습니다. 인터넷 연결을 확인하고 다시 눌러 주세요.':'음성을 인식하지 못했습니다. 버튼을 눌러 다시 말씀해 주세요.';
      if(autoConversationActive)pauseAutoConversation('음성인식 오류로 자동대화를 일시정지했습니다.');
    }
  });
  controller.side=side;activeSpeech=controller;
  if(staff)staffListening=true;else isListening=true;
  controller.start(Ctor,staff?'ko-KR':LANGS[lang].speech);
}

async function runTranslate(){
  const input=$('#inputText').value.trim(); if(!input){$('#inputText').focus();return;}
  const sKey=$('#sourceLang').value,tKey=$('#targetLang').value,status=$('#translatorStatus');
  if(sKey===tKey){status.className='status warn';status.textContent='입력 언어와 번역 언어를 다르게 선택해 주세요.';return;}
  try{
    $('#translateBtn').disabled=true;status.className='status warn';status.textContent='번역하고 있습니다…';
    const out=await translateText(input,sKey,tKey);$('#outputText').value=out;$('#outputText').dir=tKey==='ko'?'ltr':(LANGS[tKey]?.dir||'ltr');status.className='status ok';status.textContent='번역 완료 · 이름, 날짜, 금액 등 중요한 내용은 다시 확인해 주세요.';
  }catch(e){console.error(e);status.className='status warn';status.textContent=e.message==='NO_SERVER'?'번역 서버를 사용할 수 없습니다. ⚙ 설정의 연결 상태를 확인해 주세요.':e.message==='TOO_LONG'?'한 번에 3,600자까지 번역합니다. 긴 내용은 나눠 주세요.':(e.message==='UNSUPPORTED_LANG'||e.message==='INVALID_LANG_CODE')?'이 언어를 쓰려면 Apps Script Code.gs v6.0 재배포 또는 언어코드 확인이 필요합니다.':'번역하지 못했습니다. 인터넷 연결과 무료 번역 서버 설정을 확인해 주세요.';}finally{$('#translateBtn').disabled=false;}
}


// v6.8.4 수동대화 / 자동대화 (UI empty-state text only)
const CONVERSATION_MODE_STORAGE='ans_conversation_mode_v1';
let conversationMode=localStorage.getItem(CONVERSATION_MODE_STORAGE)==='auto'?'auto':'manual';
let autoConversationActive=false;
let autoConversationTurn='staff';
let autoConversationTimer=null;
function clearAutoConversationTimer(){if(autoConversationTimer){clearTimeout(autoConversationTimer);autoConversationTimer=null;}}
function updateConversationModeUI(){
  document.body.dataset.conversationMode=conversationMode;
  $('#manualModeBtn')?.classList.toggle('active',conversationMode==='manual');
  $('#autoModeBtn')?.classList.toggle('active',conversationMode==='auto');
  const b=$('#autoConversationBtn');
  if(b){b.classList.toggle('active',autoConversationActive);b.textContent=autoConversationActive?'자동대화 종료':(b.dataset.paused==='true'?'자동대화 다시 시작':'자동대화 시작');}
  if(document.body.dataset.page==='conversation'&&!autoConversationActive){
    const hint=$('#workspaceHint');
    if(hint)hint.textContent=conversationMode==='auto'?'「자동대화 시작」을 한 번 누르면 직원과 민원인 차례가 자동으로 전환됩니다.':'버튼을 누른 쪽의 음성만 듣습니다. 말씀을 마치면 3초 후 자동 번역합니다.';
    const dock=$('#dockStatus');
    if(dock)dock.textContent=conversationMode==='auto'?'자동대화 시작을 누르면 직원부터 듣습니다':'버튼을 누르고 말씀하세요';
  }
}
function setConversationMode(mode){
  if(!['manual','auto'].includes(mode))return;
  if(autoConversationActive)stopAutoConversation('대화 방식을 변경해 자동대화를 종료했습니다.');
  conversationMode=mode;localStorage.setItem(CONVERSATION_MODE_STORAGE,mode);
  const b=$('#autoConversationBtn');if(b)b.dataset.paused='false';
  updateConversationModeUI();
}
function stopAutoConversation(message='자동대화를 종료했습니다.'){
  clearAutoConversationTimer();autoConversationActive=false;
  cancelActiveSpeech();cancelStaffPendingTranslation(true);cancelVisitorPendingTranslation(true);window.speechSynthesis?.cancel();
  const b=$('#autoConversationBtn');if(b){b.dataset.paused='false';b.classList.remove('active');b.textContent='자동대화 시작';}
  const dock=$('#dockStatus');if(dock)dock.textContent=message;
  updateConversationModeUI();
}
function pauseAutoConversation(message='자동대화를 일시정지했습니다.'){
  clearAutoConversationTimer();autoConversationActive=false;
  cancelActiveSpeech();window.speechSynthesis?.cancel();
  const b=$('#autoConversationBtn');if(b){b.dataset.paused='true';b.classList.remove('active');b.textContent='자동대화 다시 시작';}
  const dock=$('#dockStatus');if(dock)dock.textContent=message;
}
function scheduleAutoTurn(side,delay=850){
  if(conversationMode!=='auto'||!autoConversationActive)return;
  clearAutoConversationTimer();autoConversationTurn=side;
  const L=LANGS[currentLang];
  $('#dockStatus').textContent=side==='staff'?'직원 차례 · 잠시 후 한국어를 듣습니다':`${L.name} 차례 · 잠시 후 상대방 말씀을 듣습니다`;
  autoConversationTimer=setTimeout(()=>{
    autoConversationTimer=null;
    if(conversationMode!=='auto'||!autoConversationActive)return;
    beginSpeech(side);
  },delay);
}
async function startAutoConversation(){
  if(autoConversationActive){stopAutoConversation();return;}
  conversationMode='auto';localStorage.setItem(CONVERSATION_MODE_STORAGE,'auto');autoConversationActive=true;autoConversationTurn='staff';
  const b=$('#autoConversationBtn');if(b){b.dataset.paused='false';b.classList.add('active');b.textContent='자동대화 종료';}
  document.body.dataset.page='conversation';showConversationSide('staff');updateConversationModeUI();
  $('#dockStatus').textContent='자동대화 시작 · 직원의 한국어를 듣습니다';
  await beginSpeech('staff');
}
function speakPhraseWithAutoFollow(text,langKey){
  if(conversationMode!=='auto')return speak(text,langKey);
  cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();
  if(!('speechSynthesis' in window)){alert('이 기기에서는 음성 읽기를 지원하지 않습니다.');return;}
  clearAutoConversationTimer();autoConversationActive=true;autoConversationTurn='visitor';updateConversationModeUI();
  speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang=LANGS[langKey]?.tts||langKey;u.rate=.92;
  u.onstart=()=>{$('#dockStatus').textContent='민원문장을 읽고 있습니다';};
  u.onend=()=>scheduleAutoTurn('visitor',850);
  u.onerror=()=>pauseAutoConversation('문장 읽기에 실패해 자동대화를 일시정지했습니다.');
  speechSynthesis.speak(u);
}

$('#search').addEventListener('input',renderList);
$('#speakBtn').onclick=async()=>{const tx=await phraseTextForAction();if(tx)speakPhraseWithAutoFollow(tx,currentLang);};
$('#copyBtn').onclick=async()=>{const tx=await phraseTextForAction();if(!tx)return;await copyText(tx);$('#copyBtn').textContent='✓ 복사됨';setTimeout(()=>$('#copyBtn').textContent='📋 복사',900);};
$('#bigBtn').onclick=openBig; $('#favBtn').onclick=()=>toggleFav(currentPhrase.id); $('#modalClose').onclick=closeBig; $('#modalSpeak').onclick=()=>{const tx=$('#modalText').textContent;if(tx)speak(tx,currentLang);}; $('#bigModal').addEventListener('click',e=>{if(e.target.id==='bigModal')closeBig();});
function setMainTab(which){
  document.body.dataset.page=which==='dynamic'?'dynamic':which==='preset'?(document.body.dataset.page==='favorites'?'favorites':'phrases'):'conversation';
  if(which==='staff'||which==='speech')document.body.dataset.activeSide=which==='staff'?'staff':'visitor';
  updateWorkspaceNavigation();
  cancelActiveSpeech();window.speechSynthesis?.cancel();
  ['presetTab','staffTab','speechTab','dynamicTab'].forEach(id=>$('#'+id).classList.toggle('active',id===which+'Tab'));
  $('#phraseView').classList.toggle('hidden',which!=='preset');
  $('#staffView').classList.toggle('active',which==='staff');
  $('#speechView').classList.toggle('active',which==='speech');
  $('#dynamicView').classList.toggle('active',which==='dynamic');
  if(which!=='speech'){stopListening(true);cancelVisitorPendingTranslation(true);}
  if(which!=='staff'){stopStaffListening(true);cancelStaffPendingTranslation(true);}
  if(which==='staff')updateStaffLanguage();
  if(which==='speech')updateSpeechLanguage();
  if(which==='dynamic')checkTranslatorStatus();
}
$('#presetTab').onclick=()=>setMainTab('preset');
$('#staffTab').onclick=()=>setMainTab('staff');
$('#speechTab').onclick=()=>setMainTab('speech');
$('#dynamicTab').onclick=()=>setMainTab('dynamic');
$('#sourceLang').addEventListener('change',checkTranslatorStatus);$('#targetLang').addEventListener('change',checkTranslatorStatus);
$('#swapBtn').onclick=()=>{const s=$('#sourceLang').value,t=$('#targetLang').value;$('#sourceLang').value=t;$('#targetLang').value=s;const a=$('#inputText').value;$('#inputText').value=$('#outputText').value;$('#outputText').value=a;checkTranslatorStatus();};
$('#translateBtn').onclick=runTranslate;$('#copyOutputBtn').onclick=()=>copyText($('#outputText').value);$('#speakOutputBtn').onclick=()=>{const k=$('#targetLang').value; if($('#outputText').value) speak($('#outputText').value,k);};$('#clearBtn').onclick=()=>{$('#inputText').value='';$('#outputText').value='';};



$('#manualModeBtn').onclick=()=>setConversationMode('manual');
$('#autoModeBtn').onclick=()=>setConversationMode('auto');
$('#autoConversationBtn').onclick=startAutoConversation;
$('#staffMicBtn').onclick=()=>{showConversationSide('staff');startStaffListening();};
$('#staffMicPermissionBtn').onclick=()=>ensureMicrophonePermission($('#staffSpeechStatus'));
$('#staffMicDiagBtn').onclick=()=>runMicDiagnostic($('#staffMicDiag'));
$('#speechMicPermissionBtn').onclick=()=>ensureMicrophonePermission($('#speechStatus'));
$('#speechMicDiagBtn').onclick=()=>runMicDiagnostic($('#speechMicDiag'));
$('#manualFromKoBtn').onclick=()=>{const t=$('#manualKoreanText').value.trim();if(!t){$('#manualKoreanText').focus();return;}$('#staffHeardText').textContent=t;translateStaffText(t);};
$('#staffSpeakResultBtn').onclick=()=>{const t=$('#staffForeignResult').textContent;if(t&&!['번역 결과가 여기에 표시됩니다.','번역하지 못했습니다.','번역 중…'].includes(t))speak(t,currentLang);};
$('#staffCopyResultBtn').onclick=()=>{const t=$('#staffForeignResult').textContent;if(t)copyText(t);};
$('#staffRetryBtn').onclick=()=>{const t=$('#manualKoreanText').value.trim()||($('#staffHeardText').textContent==='아직 인식된 내용이 없습니다.'?'':$('#staffHeardText').textContent);if(t)translateStaffText(t);};
$('#staffClearBtn').onclick=()=>{$('#staffView').dataset.filled='false';$('#staffForeignResult').dataset.ready='false';cancelActiveSpeech();window.speechSynthesis?.cancel();stopStaffListening(true);cancelStaffPendingTranslation(true);$('#staffHeardText').textContent='';$('#staffForeignResult').textContent='번역 결과가 여기에 표시됩니다.';$('#manualKoreanText').value='';$('#staffSpeechStatus').className='status';$('#staffSpeechStatus').textContent='대기 중 · 「내가 말하기」를 누를 때만 한국어 음성을 듣습니다.';};

$('#micBtn').onclick=()=>{showConversationSide('visitor');startListening();};
$('#speechPackBtn').onclick=()=>manageSpeechPack(true);
$('#manualToKoBtn').onclick=()=>{const t=$('#manualForeignText').value.trim();if(!t){$('#manualForeignText').focus();return;}$('#heardText').textContent=t;translateSpeechText(t);};
$('#retryTranslateBtn').onclick=()=>{const t=$('#manualForeignText').value.trim()||($('#heardText').textContent==='아직 인식된 내용이 없습니다.'?'':$('#heardText').textContent);if(t)translateSpeechText(t);};
$('#speakKoreanBtn').onclick=()=>{const t=$('#koreanResult').textContent;if(t&&!['번역 결과가 여기에 표시됩니다.','번역하지 못했습니다.','번역 중…'].includes(t))speakKorean(t);};
$('#copyKoreanBtn').onclick=()=>{const t=$('#koreanResult').textContent;if(t)copyText(t);};
$('#clearSpeechBtn').onclick=()=>{$('#speechView').dataset.filled='false';$('#koreanResult').dataset.ready='false';cancelActiveSpeech();window.speechSynthesis?.cancel();stopListening(true);cancelVisitorPendingTranslation(true);$('#heardText').textContent='';$('#koreanResult').textContent='번역 결과가 여기에 표시됩니다.';$('#manualForeignText').value='';$('#speechStatus').className='status';$('#speechStatus').textContent='대기 중 · 언어를 선택하고 직원이 「말씀하세요」를 눌러야 마이크가 켜집니다.';};


function normLangText(v){return String(v||'').trim().toLowerCase().replace(/\s+/g,' ');}
function isDuplicateLanguage(name,native,gas,speech,ignoreKey=''){
  const n=normLangText(name), nn=normLangText(native), g=normLangText(gas), sp=normLangText(speech);
  return Object.entries(LANGS).some(([k,v])=>{
    if(k===ignoreKey)return false;
    const vn=normLangText(v.name), vnn=normLangText(v.native), vg=normLangText(v.gas), vsp=normLangText(v.speech);
    return (n && (vn===n || vnn===n)) || (nn && (vn===nn || vnn===nn)) || (g && sp && vg===g && vsp===sp);
  });
}
function removeCustomLanguage(k,ask=true){
  const v=LANGS[k];
  if(!v?.custom)return false;
  if(ask && !confirm(`${v.name}을(를) 목록에서 삭제할까요?`))return false;
  delete LANGS[k];
  saveCustomLanguages();
  if(currentLang===k){cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();clearConversationDisplay();currentLang='en';localStorage.setItem('ans_lang','en');}
  buildLanguages();populateDynamic();renderPhrase();updateSpeechLanguage();updateStaffLanguage();renderCustomLanguageList();
  const status=$('#customLangStatus');
  if(status){status.className='status ok';status.textContent=`✓ ${v.name}을(를) 삭제했습니다.`;}
  return true;
}
function cleanupDuplicateCustomLanguages(){
  const seenNames=new Set();
  const seenCombos=new Set();
  // 기본 언어를 기준으로 먼저 등록
  Object.entries(LANGS).filter(([k,v])=>!v.custom).forEach(([k,v])=>{
    [v.name,v.native].forEach(x=>{const n=normLangText(x);if(n)seenNames.add(n);});
    const combo=normLangText(v.gas)+'|'+normLangText(v.speech);if(combo!=='|')seenCombos.add(combo);
  });
  let changed=false;
  Object.entries(LANGS).filter(([k,v])=>v.custom).forEach(([k,v])=>{
    const names=[normLangText(v.name),normLangText(v.native)].filter(Boolean);
    const combo=normLangText(v.gas)+'|'+normLangText(v.speech);
    const dup=names.some(n=>seenNames.has(n)) || (combo!=='|' && seenCombos.has(combo));
    if(dup){delete LANGS[k];changed=true;return;}
    names.forEach(n=>seenNames.add(n));if(combo!=='|')seenCombos.add(combo);
  });
  if(changed){saveCustomLanguages();}
  return changed;
}
function customLangKey(){return 'custom_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,6);}
function renderCustomLanguageList(){
  const list=$('#customLangList');if(!list)return;list.innerHTML='';
  const items=Object.entries(LANGS).filter(([k,v])=>v.custom);
  if(!items.length){list.innerHTML='<div class="empty-state small">직접 추가한 언어가 없습니다.</div>';return;}
  items.forEach(([k,v])=>{
    const row=document.createElement('div');row.className='custom-lang-item';
    row.innerHTML=`<div><b>${escapeHtml(v.name)}</b><span>${escapeHtml(v.native)} · 번역 ${escapeHtml(v.gas)} · 음성 ${escapeHtml(v.speech)}</span></div><button class="btn danger" type="button">삭제</button>`;
    row.querySelector('button').onclick=()=>removeCustomLanguage(k,true);
    list.appendChild(row);
  });
}
function openLanguageManager(){if(autoConversationActive)stopAutoConversation('언어 설정을 열어 자동대화를 종료했습니다.');renderCustomLanguageList();$('#languageModal').classList.add('show');}
function closeLanguageManager(){$('#languageModal').classList.remove('show');setBottomNavActive('home');}
function addCustomLanguage(){
  const name=$('#customLangName').value.trim();
  const native=$('#customLangNative').value.trim()||name;
  const gas=$('#customLangCode').value.trim();
  const speech=$('#customLangSpeech').value.trim();
  const dir=$('#customLangDir').value==='rtl'?'rtl':'ltr';
  const status=$('#customLangStatus');
  if(!name||!gas||!speech){status.className='status warn';status.textContent='언어 이름, 번역코드, 음성코드를 모두 입력해 주세요.';return;}
  if(!/^[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?$/.test(gas)){status.className='status warn';status.textContent='번역코드는 fr, de, pt, hi 같은 형식으로 입력해 주세요.';return;}
  if(!/^[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})+$/.test(speech)){status.className='status warn';status.textContent='음성코드는 fr-FR, de-DE, pt-BR처럼 국가/지역을 포함해 입력해 주세요.';return;}
  if(isDuplicateLanguage(name,native,gas,speech)){status.className='status warn';status.textContent='이미 같은 언어가 기본 목록 또는 직접 추가 목록에 있습니다. 중복으로 추가하지 않았습니다.';return;}
  const key=customLangKey();
  LANGS[key]={name,native,tts:speech,speech,api:gas,gas,dir,custom:true};
  saveCustomLanguages();
  cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();clearConversationDisplay();currentLang=key;localStorage.setItem('ans_lang',key);
  buildLanguages();populateDynamic();renderPhrase();updateSpeechLanguage();updateStaffLanguage();renderCustomLanguageList();
  $('#customLangName').value='';$('#customLangNative').value='';$('#customLangCode').value='';$('#customLangSpeech').value='';
  status.className='status ok';status.textContent=`✓ ${name}을(를) 추가했습니다. 현재 상대방 언어로 선택했습니다.`;
}
$('#customLangPreset').addEventListener('change',e=>{
  const v=e.target.value;if(!v)return;
  const [gas,name,native,speech,dir]=v.split('|');
  $('#customLangName').value=name||'';$('#customLangNative').value=native||'';$('#customLangCode').value=gas||'';$('#customLangSpeech').value=speech||'';$('#customLangDir').value=dir||'ltr';
  $('#customLangStatus').className='status';$('#customLangStatus').textContent='예시 값을 채웠습니다. 필요하면 수정한 뒤 「언어 추가」를 누르세요.';
});
$('#languageManageBtn').onclick=openLanguageManager;
$('#deleteSelectedLangBtn').onclick=()=>{const k=$('#deleteSelectedLangBtn').dataset.lang||currentLang;removeCustomLanguage(k,true);};
$('#languageModalClose').onclick=closeLanguageManager;
$('#languageModal').addEventListener('click',e=>{if(e.target.id==='languageModal')closeLanguageManager();});
$('#addCustomLangBtn').onclick=addCustomLanguage;


// 기본 민원문구 수정 (삭제는 허용하지 않음)
let editingBuiltinPhraseId=null;
function builtinPhraseById(id){return P.find(x=>x.id===id&&!x.customPhrase)||null;}
function openBuiltinPhraseEditor(id){
  const p=builtinPhraseById(id);if(!p)return;
  editingBuiltinPhraseId=id;
  $('#builtinPhraseCategory').textContent=p.cat||'';
  $('#builtinPhraseText').value=p.ko;
  $('#builtinPhraseStatus').className='status';
  $('#builtinPhraseStatus').textContent='기본문구를 수정하면 이 기기에 저장되고, 선택한 외국어는 번역 서버를 통해 다시 번역됩니다.';
  $('#builtinPhraseModal').classList.add('show');
  setTimeout(()=>$('#builtinPhraseText')?.focus(),80);
}
function closeBuiltinPhraseEditor(){editingBuiltinPhraseId=null;$('#builtinPhraseModal').classList.remove('show');}
async function saveBuiltinPhraseEdit(){
  const p=builtinPhraseById(editingBuiltinPhraseId);if(!p)return;
  const status=$('#builtinPhraseStatus');const ko=$('#builtinPhraseText').value.trim();
  if(!ko){status.className='status warn';status.textContent='문구를 입력해 주세요.';return;}
  if(ko.length>180){status.className='status warn';status.textContent='문구는 180자 이내로 입력해 주세요.';return;}
  p.ko=ko;
  const def=schoolDefaultText(p,getSchoolName());
  p.builtinEdited=(ko!==def);
  clearPhraseTranslationCache(p.id);saveBuiltinPhraseOverrides();
  currentPhrase=p;buildFilters();renderPhrase();
  status.className='status ok';status.textContent='✓ 기본문구 수정 내용을 저장했습니다.';
}
function restoreBuiltinPhrase(){
  const p=builtinPhraseById(editingBuiltinPhraseId);if(!p)return;
  p.ko=schoolDefaultText(p,getSchoolName());p.builtinEdited=false;
  clearPhraseTranslationCache(p.id);saveBuiltinPhraseOverrides();
  $('#builtinPhraseText').value=p.ko;currentPhrase=p;buildFilters();renderPhrase();
  const status=$('#builtinPhraseStatus');status.className='status ok';status.textContent='✓ 이 문구를 기본값으로 복원했습니다.';
}
function syncSchoolNameUI(){
  const name=getSchoolName();
  const input=$('#schoolNameInput');if(input)input.value=name;
  const header=document.querySelector('.school-name');if(header)header.textContent=name;
}
function saveSchoolNameSetting(){
  const input=$('#schoolNameInput'),status=$('#schoolNameStatus');
  const newName=(input?.value||'').trim();
  if(!newName){status.className='status warn';status.textContent='학교 이름을 입력해 주세요.';return;}
  const oldName=getSchoolName();
  localStorage.setItem(SCHOOL_NAME_STORAGE,newName);
  applySchoolNameChange(oldName,newName);syncSchoolNameUI();renderPhrase();buildFilters();
  status.className='status ok';status.textContent='✓ 학교 이름을 저장했습니다. 학교명이 들어간 기본문구에도 자동 반영했습니다.';
}

// v6.2 사용자 즐겨찾기 문구 등록/수정/삭제
let editingCustomPhraseId=null;
let customPhraseRecognition=null;
function customPhraseById(id){return P.find(x=>x.id===id&&x.customPhrase)||null;}
function renderCustomPhraseList(){
  const list=$('#customPhraseList');if(!list)return;list.innerHTML='';
  const items=P.filter(x=>x.customPhrase);
  if(!items.length){list.innerHTML='<div class="empty-state small">직접 등록한 문구가 없습니다.</div>';return;}
  items.forEach(p=>{
    const row=document.createElement('div');row.className='custom-phrase-item';
    row.innerHTML=`<div><b>${escapeHtml(p.ko)}</b><span>${escapeHtml(p.cat)} · ${favorites.has(p.id)?'⭐ 즐겨찾기':'즐겨찾기 해제됨'}</span></div><div class="custom-phrase-item-actions"><button class="btn" type="button">수정</button><button class="btn danger" type="button">삭제</button></div>`;
    const [edit,del]=row.querySelectorAll('button');edit.onclick=()=>openCustomPhraseManager(p.id);del.onclick=()=>deleteCustomPhrase(p.id);
    list.appendChild(row);
  });
}
function resetCustomPhraseForm(){
  editingCustomPhraseId=null;$('#customPhraseModalTitle').textContent='⭐ 내 문구 등록';$('#customPhraseCategory').value='기타';$('#customPhraseText').value='';$('#saveCustomPhraseBtn').textContent='⭐ 즐겨찾기로 저장';
  $('#customPhraseStatus').className='status';$('#customPhraseStatus').textContent='등록한 문구는 이 스마트폰에 저장됩니다. 주민등록번호·여권번호 등 민감정보는 문구로 저장하지 마세요.';
}
function openCustomPhraseManager(id=null){
  resetCustomPhraseForm();
  if(id){const p=customPhraseById(id);if(p){editingCustomPhraseId=id;$('#customPhraseModalTitle').textContent='✏ 내 문구 수정';$('#customPhraseCategory').value=p.cat;$('#customPhraseText').value=p.ko;$('#saveCustomPhraseBtn').textContent='수정 내용 저장';}}
  renderCustomPhraseList();$('#customPhraseModal').classList.add('show');
  setTimeout(()=>$('#customPhraseText')?.focus(),80);
}
function closeCustomPhraseManager(){try{customPhraseRecognition?.stop();}catch{}$('#customPhraseModal').classList.remove('show');}
function normalizePhraseText(v){return String(v||'').trim().replace(/\s+/g,' ').toLowerCase();}
async function saveCustomPhraseFromForm(){
  const status=$('#customPhraseStatus');const ko=$('#customPhraseText').value.trim();const cat=$('#customPhraseCategory').value||'기타';
  if(!ko){status.className='status warn';status.textContent='등록할 한국어 문구를 입력하거나 「말로 입력」을 이용해 주세요.';return;}
  if(ko.length>180){status.className='status warn';status.textContent='문구는 180자 이내로 등록해 주세요.';return;}
  const norm=normalizePhraseText(ko);const dup=P.find(x=>x.id!==editingCustomPhraseId&&normalizePhraseText(x.ko)===norm);
  if(dup){status.className='status warn';status.textContent='이미 같은 문구가 등록되어 있습니다.';return;}
  let p;
  if(editingCustomPhraseId){
    p=customPhraseById(editingCustomPhraseId);if(!p)return;
    const changed=p.ko!==ko;if(changed)clearPhraseTranslationCache(p.id);p.ko=ko;p.cat=cat;p.updatedAt=Date.now();
  }else{
    p={id:makeCustomPhraseId(),cat,ko,t:{},customPhrase:true,createdAt:Date.now(),updatedAt:Date.now()};P.push(p);favorites.add(p.id);saveFavoriteIds();editingCustomPhraseId=p.id;
  }
  favorites.add(p.id);saveFavoriteIds();saveCustomPhrases();currentPhrase=p;buildFilters();renderPhrase();renderCustomPhraseList();
  status.className='status ok';status.textContent='✓ 저장했습니다. 즐겨찾기와 「내 문구」에서 바로 사용할 수 있습니다.';
  $('#customPhraseModalTitle').textContent='✏ 내 문구 수정';$('#saveCustomPhraseBtn').textContent='수정 내용 저장';
  if(getServerUrl()){
    try{status.textContent='✓ 문구 저장 완료 · 현재 선택 언어로 번역을 준비하고 있습니다…';await ensurePhraseTranslation(p,currentLang);status.textContent='✓ 문구 저장 및 현재 선택 언어 번역 완료.';renderPhrase();}
    catch(e){status.className='status warn';status.textContent='문구는 저장했습니다. 번역 서버 연결 상태를 확인하면 외국어 번역도 사용할 수 있습니다.';}
  }
}
function deleteCustomPhrase(id){
  const p=customPhraseById(id);if(!p)return;
  if(!confirm(`「${p.ko}」\n\n이 문구를 삭제할까요?`))return;
  const i=P.findIndex(x=>x.id===id);if(i>=0)P.splice(i,1);favorites.delete(id);saveFavoriteIds();clearPhraseTranslationCache(id);saveCustomPhrases();
  if(currentPhrase.id===id)currentPhrase=P[0];if(editingCustomPhraseId===id)resetCustomPhraseForm();buildFilters();renderPhrase();renderCustomPhraseList();
  const status=$('#customPhraseStatus');if(status){status.className='status ok';status.textContent='✓ 문구를 삭제했습니다.';}
}
async function startCustomPhraseSpeech(){
  const status=$('#customPhraseStatus'),btn=$('#customPhraseMicBtn'),Ctor=getRecognitionCtor();
  if(!Ctor){status.className='status warn';status.textContent='이 브라우저는 음성입력을 지원하지 않습니다. 한국어 문구를 직접 입력해 주세요.';return;}
  if(!(await ensureMicrophonePermission(status)))return;
  try{customPhraseRecognition?.stop();}catch{}
  const r=customPhraseRecognition=new Ctor();r.lang='ko-KR';r.interimResults=true;r.continuous=false;r.maxAlternatives=1;
  r.onstart=()=>{btn.textContent='■ 듣는 중';btn.classList.add('listening');status.className='status ok';status.textContent='한국어로 문구를 말씀하세요.';};
  r.onresult=e=>{let final='',interim='';for(let i=e.resultIndex;i<e.results.length;i++){const t=e.results[i][0].transcript;e.results[i].isFinal?final+=t:interim+=t;}const text=(final||interim).trim();if(text)$('#customPhraseText').value=text;};
  r.onerror=e=>{status.className='status warn';status.textContent=e.error==='not-allowed'?'마이크 권한이 차단되었습니다.':'음성을 인식하지 못했습니다. 다시 눌러 말씀해 주세요.';};
  r.onend=()=>{btn.textContent='🎤 말로 입력';btn.classList.remove('listening');if($('#customPhraseText').value.trim()){status.className='status ok';status.textContent='음성 입력이 완료되었습니다. 문구를 확인한 뒤 저장하세요.';}};
  try{r.start();}catch(e){btn.textContent='🎤 말로 입력';btn.classList.remove('listening');status.className='status warn';status.textContent='음성입력을 시작하지 못했습니다. 다시 시도해 주세요.';}
}
$('#customPhraseManageBtn').onclick=()=>openCustomPhraseManager();
$('#customPhraseModalClose').onclick=closeCustomPhraseManager;
$('#customPhraseModal').addEventListener('click',e=>{if(e.target.id==='customPhraseModal')closeCustomPhraseManager();});
$('#saveCustomPhraseBtn').onclick=saveCustomPhraseFromForm;
$('#customPhraseMicBtn').onclick=startCustomPhraseSpeech;
$('#editPhraseBtn').onclick=()=>{currentPhrase.customPhrase?openCustomPhraseManager(currentPhrase.id):openBuiltinPhraseEditor(currentPhrase.id);};
$('#deletePhraseBtn').onclick=()=>{if(currentPhrase.customPhrase)deleteCustomPhrase(currentPhrase.id);};
$('#builtinPhraseSaveBtn').onclick=saveBuiltinPhraseEdit;
$('#builtinPhraseRestoreBtn').onclick=restoreBuiltinPhrase;
$('#builtinPhraseClose').onclick=closeBuiltinPhraseEditor;
$('#builtinPhraseModal').addEventListener('click',e=>{if(e.target.id==='builtinPhraseModal')closeBuiltinPhraseEditor();});
$('#saveSchoolNameBtn').onclick=saveSchoolNameSetting;

function setBottomNavActive(name){
  document.querySelectorAll('.bottom-nav-btn').forEach(b=>b.classList.toggle('active',b.dataset.nav===name));
}
function scrollToAppTarget(el){
  if(!el)return;
  const y=el.getBoundingClientRect().top+window.scrollY-12;
  window.scrollTo({top:Math.max(0,y),behavior:'smooth'});
}
document.querySelectorAll('.bottom-nav-btn').forEach(btn=>{
  btn.onclick=()=>{
    const nav=btn.dataset.nav;
    if(nav==='home'){
      setMainTab('staff');currentCat='전체';buildFilters();renderList();setBottomNavActive('home');window.scrollTo({top:0,behavior:'smooth'});
    }else if(nav==='phrases'){
      setMainTab('preset');currentCat='전체';buildFilters();renderList();setBottomNavActive('phrases');scrollToAppTarget(document.querySelector('.grid>section.card:first-child'));
    }else if(nav==='favorites'){
      setMainTab('preset');currentCat='즐겨찾기';buildFilters();renderList();setBottomNavActive('favorites');scrollToAppTarget(document.querySelector('.grid>section.card:first-child'));
    }else if(nav==='language'){
      setBottomNavActive('language');openLanguageManager();
    }else if(nav==='settings'){
      setBottomNavActive('settings');syncServerInputs();syncSchoolNameUI();$('#settingsModal').classList.add('show');testServer('#serverStatus');
    }
  };
});

$('#settingsBtn').onclick=()=>{ if(autoConversationActive)stopAutoConversation('설정을 열어 자동대화를 종료했습니다.'); syncServerInputs(); syncSchoolNameUI(); $('#settingsModal').classList.add('show'); testServer('#serverStatus'); };
$('#settingsClose').onclick=()=>{$('#settingsModal').classList.remove('show');setBottomNavActive('home');};
$('#settingsModal').addEventListener('click',e=>{if(e.target.id==='settingsModal')$('#settingsModal').classList.remove('show');});
$('#saveServerBtn').onclick=()=>saveServerFromInput('#serverUrlInput','#serverStatus');
const serverTestBtn=$('#serverTestBtn'); if(serverTestBtn)serverTestBtn.onclick=()=>testServer('#serverStatus');
$('#quickSaveServerBtn').onclick=()=>saveServerFromInput('#quickServerUrlInput','#quickServerStatus');
$('#quickSettingsHelp').onclick=()=>{syncServerInputs();$('#settingsModal').classList.add('show');};
$('#clearServerBtn').onclick=async()=>{
  localStorage.removeItem('ans_gas_url'); syncServerInputs();
  $('#serverStatus').className='status';$('#serverStatus').textContent='기본 연결주소로 복원했습니다. 연결을 확인하고 있습니다…';
  const q=$('#quickServerStatus');if(q){q.className='status';q.textContent='기본 연결주소 사용 중';}
  setQuickServerBadge('checking','확인 중'); checkTranslatorStatus();
  await testServerEverywhere();
  await testServer('#serverStatus');
};

$('#howInstallBtn').onclick=()=>$('#installModal').classList.add('show');
$('#installClose').onclick=()=>$('#installModal').classList.remove('show');
$('#installModal').addEventListener('click',e=>{if(e.target.id==='installModal')$('#installModal').classList.remove('show');});

function isStandaloneApp(){
  return !!(window.matchMedia?.('(display-mode: standalone)')?.matches || navigator.standalone);
}
function syncInstallButton(){
  const btn=$('#installBtn'); if(!btn)return;
  // v6.8.7: 설치 여부/브라우저 종류와 관계없이 상단의 앱 설치 버튼을 항상 표시합니다.
  btn.style.display='block';
  btn.classList.add('show-install','always');
}
window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferredPrompt=e;syncInstallButton();});
window.addEventListener('appinstalled',()=>{deferredPrompt=null;syncInstallButton();});
$('#installBtn').onclick=async()=>{
  if(deferredPrompt && !isStandaloneApp()){
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    deferredPrompt=null;
    syncInstallButton();
    return;
  }
  // 카카오톡 등 인앱 브라우저나 이미 설치된 앱에서는 설치 방법을 안내합니다.
  $('#installModal').classList.add('show');
};
syncInstallButton();
if('serviceWorker' in navigator && location.protocol!=='file:'){window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));}

syncSchoolNameUI();buildLanguages();buildFilters();renderPhrase();populateDynamic();updateSpeechLanguage();updateStaffLanguage();syncServerInputs();testServerEverywhere();

setMainTab('staff');
window.addEventListener('pagehide',()=>{clearAutoConversationTimer();autoConversationActive=false;cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();window.speechSynthesis?.cancel();});
document.addEventListener('visibilitychange',()=>{if(document.hidden){if(autoConversationActive)stopAutoConversation('앱이 백그라운드로 이동해 자동대화를 종료했습니다.');else{clearAutoConversationTimer();cancelActiveSpeech();cancelStaffPendingTranslation();cancelVisitorPendingTranslation();window.speechSynthesis?.cancel();}}});

const speechVersionOK=window.SpeechSession?.VERSION==='6.7';
document.querySelectorAll('.runtime-version').forEach(el=>{el.textContent=speechVersionOK?'v6.8.7 · 모바일 앱 설치 버튼 표시 수정 · 반복 인식 보호 유지':'업데이트 확인 필요 · 새로고침해 주세요';});
if(!speechVersionOK){$('#staffMicBtn').disabled=true;$('#micBtn').disabled=true;}

// Conversation layout: existing control IDs and storage remain compatible.
function syncConversationLanguage(){
  const select=$('#conversationLanguage');if(!select)return;
  select.replaceChildren();
  Object.entries(LANGS).forEach(([key,lang])=>{
    const option=document.createElement('option');option.value=key;option.textContent=lang.name;select.appendChild(option);
  });
  select.value=currentLang;
  const meta=document.querySelector('.dock-meta>span:first-child');
  if(meta)meta.textContent='한국어 ⇄ '+LANGS[currentLang].name;
}
function updateWorkspaceNavigation(){
  const page=document.body.dataset.page;
  document.querySelectorAll('[data-page-nav]').forEach(button=>{button.classList.toggle('selected',button.dataset.pageNav===page);button.setAttribute('aria-current',button.dataset.pageNav===page?'page':'false');});
  const labels={conversation:['대화를 시작해 주세요','말씀을 마치면 3초 후 자동 번역합니다.'],phrases:['자주 쓰는 민원문장','문장을 선택하거나 바로 읽어 주세요.'],favorites:['즐겨찾기','저장한 문장을 빠르게 찾아보세요.'],dynamic:['직접 입력해서 번역','음성 대신 글로 대화할 수 있습니다.']};
  const pair=labels[page]||labels.conversation;
  $('#workspaceTitle').textContent=pair[0];$('#workspaceHint').textContent=pair[1];
}
function showConversationSide(side){
  document.body.dataset.page='conversation';document.body.dataset.activeSide=side;
  $(side==='staff'?'#staffView':'#speechView').dataset.filled='true';
  updateWorkspaceNavigation();updateConversationModeUI();updateDockStatus();
}
function clearConversationDisplay(){
  for(const staff of [true,false]){
    $(staff?'#staffView':'#speechView').dataset.filled='false';
    $(staff?'#staffHeardText':'#heardText').textContent='';
    const result=$(staff?'#staffForeignResult':'#koreanResult');result.textContent='번역문이 여기에 표시됩니다.';result.dataset.ready='false';
    $(staff?'#manualKoreanText':'#manualForeignText').value='';
    $(staff?'#staffEditor':'#visitorEditor').open=false;
    $(staff?'#staffSpeechStatus':'#speechStatus').textContent='버튼을 누를 때만 음성을 듣습니다.';
  }
}
function openWorkspacePage(page){
  if(page!=='conversation'&&autoConversationActive)stopAutoConversation('다른 메뉴로 이동해 자동대화를 종료했습니다.');
  if(page==='conversation'){setMainTab('staff');}
  else if(page==='dynamic'){setMainTab('dynamic');}
  else{
    document.body.dataset.page=page;setMainTab('preset');
    currentCat=page==='favorites'?'즐겨찾기':'전체';buildFilters();renderList();
  }
  updateWorkspaceNavigation();updateDockStatus();window.scrollTo({top:0,behavior:'smooth'});
}
function updateDockStatus(){
  const staff=document.body.dataset.activeSide!=='visitor';
  const text=$(staff?'#staffSpeechStatus':'#speechStatus').textContent;
  let short='버튼을 누르고 말씀하세요';
  if(/수정|반복|확정되지|실패|못했|차단|끊겼/.test(text))short='인식한 문장을 확인해 주세요';
  else if(/초 후 번역/.test(text))short=(text.match(/(\d)초 후 번역/)?.[1]||'3')+'초 후 번역 · 이어서 말씀하셔도 됩니다';
  else if(/번역 중/.test(text))short=text;
  else if(/읽는 중/.test(text))short='번역문을 읽고 있습니다';
  else if(/번역 완료/.test(text))short='번역 완료'+(text.match(/번역에 ([\d.]+)초/) ? ' · '+text.match(/번역에 ([\d.]+)초/)[1]+'초' : '');
  else if(/듣는 중/.test(text))short=staff?'한국어를 듣고 있습니다':'상대방 말씀을 듣고 있습니다';
  else if(/인식한 문장/.test(text))short='인식 결과 확인 중';
  if($('#dockStatus').textContent!==short)$('#dockStatus').textContent=short;
  for(const side of ['staff','visitor']){
    const isStaff=side==='staff',ready=$(isStaff?'#staffForeignResult':'#koreanResult').dataset.ready==='true';
    $(isStaff?'#staffSpeakResultBtn':'#speakKoreanBtn').disabled=!ready;
    $(isStaff?'#staffCopyResultBtn':'#copyKoreanBtn').disabled=!ready;
  }
}
$('#conversationLanguage').onchange=e=>{
  if(autoConversationActive)stopAutoConversation('언어를 변경해 자동대화를 종료했습니다.');
  const key=e.target.value,index=Object.keys(LANGS).indexOf(key);
  document.querySelectorAll('#langBar .lang')[index]?.click();
};
document.querySelectorAll('[data-page-nav]').forEach(button=>button.onclick=()=>openWorkspacePage(button.dataset.pageNav));
document.querySelectorAll('[data-editor]').forEach(button=>button.onclick=()=>{
  const editor=$('#'+button.dataset.editor);editor.open=!editor.open;
  if(editor.open)editor.querySelector('textarea').focus();
});
// Keep old navigation hooks available to settings and compatibility controls.
document.querySelectorAll('.bottom-nav-btn').forEach(button=>{
  if(['home','phrases','favorites'].includes(button.dataset.nav))button.onclick=()=>openWorkspacePage(button.dataset.nav==='home'?'conversation':button.dataset.nav);
});
const layoutObserver=new MutationObserver(updateDockStatus);
['staffSpeechStatus','speechStatus','staffForeignResult','koreanResult'].forEach(id=>layoutObserver.observe($('#'+id),{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['data-ready']}));
updateWorkspaceNavigation();updateDockStatus();
