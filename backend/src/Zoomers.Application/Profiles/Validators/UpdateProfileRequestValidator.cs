using FluentValidation;
using Zoomers.Application.Profiles.Dtos;

namespace Zoomers.Application.Profiles.Validators;

public sealed class UpdateProfileRequestValidator : AbstractValidator<UpdateProfileRequest>
{
    public UpdateProfileRequestValidator()
    {
        When(x => x.DisplayName is not null, () =>
            RuleFor(x => x.DisplayName!)
                .NotEmpty().WithMessage("Display name cannot be empty.")
                .MaximumLength(50));

        When(x => x.Bio is not null, () =>
            RuleFor(x => x.Bio!).MaximumLength(300));

        When(x => x.Website is not null, () =>
            RuleFor(x => x.Website!).MaximumLength(200));
    }
}
