CREATE TABLE allowed_media_type (
  media_type VARCHAR(256) PRIMARY KEY
);

INSERT INTO allowed_media_type (media_type) VALUES
  ('application/octet-stream'),
  ('application/pdf'),
  ('application/rtf'),
  ('application/epub+zip'),
  ('application/x-mobipocket-ebook'),
  ('application/vnd.oasis.opendocument.text'),
  ('application/vnd.oasis.opendocument.presentation'),
  ('application/vnd.oasis.opendocument.spreadsheet'),
  ('application/vnd.oasis.opendocument.graphics'),
  ('application/msword'),
  ('application/vnd.openxmlformats-officedocument.wordprocessingml.document'),
  ('application/vnd.openxmlformats-officedocument.wordprocessingml.template'),
  ('application/vnd.ms-word.document.macroEnabled.12'),
  ('application/vnd.ms-word.template.macroEnabled.12'),
  ('application/vnd.ms-excel'),
  ('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
  ('application/vnd.openxmlformats-officedocument.spreadsheetml.template'),
  ('application/vnd.ms-excel.sheet.macroEnabled.12'),
  ('application/vnd.ms-excel.template.macroEnabled.12'),
  ('application/vnd.ms-excel.addin.macroEnabled.12'),
  ('application/vnd.ms-excel.sheet.binary.macroEnabled.12'),
  ('application/vnd.ms-powerpoint'),
  ('application/vnd.openxmlformats-officedocument.presentationml.presentation'),
  ('application/vnd.openxmlformats-officedocument.presentationml.template'),
  ('application/vnd.openxmlformats-officedocument.presentationml.slideshow'),
  ('application/vnd.ms-powerpoint.addin.macroEnabled.12'),
  ('application/vnd.ms-powerpoint.presentation.macroEnabled.12'),
  ('application/vnd.ms-powerpoint.slideshow.macroEnabled.12');
