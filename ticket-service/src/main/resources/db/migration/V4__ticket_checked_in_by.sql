-- Người soát vé, để báo "đã check-in lúc ... bởi ..." khi quét lại.
alter table tickets add column checked_in_by uuid;
